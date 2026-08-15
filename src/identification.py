"""
Identification des conducteurs - script dédié
==================================================
Ne fait QUE l'identification — pas d'enrôlement ici. Utilise
../models/enrolled_speakers.pkl généré par enrollment.py.

Amélioration continue (adaptation incrémentale) :
-----------------------------------------------------
Quand une identification est très confiante (score au-dessus de
CONFIDENT_THRESHOLD, plus strict que le seuil d'acceptation normal),
on considère que c'est probablement correct et on met à jour le profil
du conducteur avec une moyenne mobile (exponential moving average) :

    nouveau_profil = (1 - alpha) * ancien_profil + alpha * embedding_actuel

Ça permet au système de s'adapter progressivement (voix qui change
légèrement, conditions acoustiques, micro) SANS ré-entraînement complet
et sans risque de "dérive" (alpha est volontairement petit, et on
n'adapte que sur des identifications déjà très sûres).

Important : ce n'est PAS un vrai ré-entraînement du modèle ECAPA-TDNN
(ça, ce serait très coûteau et inutile ici) — c'est une mise à jour du
"template" de référence de chaque conducteur, ce qui est l'équivalent
pratique pour ce genre de système par embeddings.
"""

import os
import pickle
import argparse

from common import load_model, get_embedding, cosine_similarity


# Seuil d'acceptation normal : en dessous, on rejette comme "INCONNU"
ACCEPT_THRESHOLD = 0.25

# Seuil de confiance pour déclencher l'adaptation : volontairement plus
# strict que ACCEPT_THRESHOLD, pour n'adapter que sur des cas très sûrs
CONFIDENT_THRESHOLD = 0.45

# Poids de la mise à jour (moyenne mobile). Petit = adaptation lente et
# prudente. À calibrer : 0.05-0.15 est un bon point de départ.
ADAPTATION_ALPHA = 0.1


def load_enrolled(path):
    with open(path, "rb") as f:
        return pickle.load(f)


def save_enrolled(speaker_embeddings, path):
    with open(path, "wb") as f:
        pickle.dump(speaker_embeddings, f)


def identify(classifier, audio_path, enrolled_speakers):
    """
    Retourne (speaker_name, best_score, all_scores, embedding).
    On retourne aussi l'embedding pour pouvoir l'utiliser dans
    l'adaptation sans le recalculer.
    """
    embedding = get_embedding(classifier, audio_path)

    scores = {
        name: cosine_similarity(embedding, ref_emb)
        for name, ref_emb in enrolled_speakers.items()
    }

    best_speaker = max(scores, key=scores.get)
    best_score = scores[best_speaker]

    if best_score < ACCEPT_THRESHOLD:
        return "INCONNU", best_score, scores, embedding

    return best_speaker, best_score, scores, embedding


def adapt_profile(enrolled_speakers, speaker_name, new_embedding, alpha=ADAPTATION_ALPHA):
    """
    Met à jour le profil d'un conducteur avec une moyenne mobile vers le
    nouvel embedding. Modifie enrolled_speakers en place.
    """
    old_embedding = enrolled_speakers[speaker_name]
    enrolled_speakers[speaker_name] = (1 - alpha) * old_embedding + alpha * new_embedding


def identify_and_adapt(classifier, audio_path, enrolled_speakers, save_path=None):
    """
    Identifie un échantillon, et adapte le profil du conducteur si la
    confiance est suffisante. Sauvegarde le fichier mis à jour si
    save_path est fourni et qu'une adaptation a eu lieu.

    Retourne (speaker_name, best_score, all_scores, adapted: bool).
    """
    speaker, score, scores, embedding = identify(classifier, audio_path, enrolled_speakers)

    adapted = False
    if speaker != "INCONNU" and score >= CONFIDENT_THRESHOLD:
        adapt_profile(enrolled_speakers, speaker, embedding)
        adapted = True
        if save_path:
            save_enrolled(enrolled_speakers, save_path)

    return speaker, score, scores, adapted


def main():
    parser = argparse.ArgumentParser(description="Identification d'un conducteur")
    parser.add_argument("audio_file", help="Chemin du fichier audio à identifier")
    parser.add_argument("--models", default="../models/enrolled_speakers.pkl")
    parser.add_argument("--pretrained", default="../pretrained_ecapa")
    parser.add_argument("--no-adapt", action="store_true",
                         help="Désactive l'adaptation incrémentale")
    args = parser.parse_args()

    print("=== Chargement du modèle ECAPA-TDNN ===")
    classifier = load_model(savedir=args.pretrained)

    print("=== Chargement des conducteurs enrôlés ===")
    enrolled_speakers = load_enrolled(args.models)
    print(f"Conducteurs : {list(enrolled_speakers.keys())}")

    print("\n=== Identification ===")
    if args.no_adapt:
        speaker, score, scores, embedding = identify(classifier, args.audio_file, enrolled_speakers)
        adapted = False
    else:
        speaker, score, scores, adapted = identify_and_adapt(
            classifier, args.audio_file, enrolled_speakers, save_path=args.models
        )

    print(f"Conducteur identifié : {speaker} (score: {score:.3f})")
    print("Scores détaillés :")
    for name, s in sorted(scores.items(), key=lambda x: -x[1]):
        print(f"  {name}: {s:.3f}")

    if adapted:
        print(f"[OK] Profil de '{speaker}' mis à jour (identification très confiante)")


if __name__ == "__main__":
    main()

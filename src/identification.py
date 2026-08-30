"""
Identification des conducteurs - script dédié
==================================================
Ne fait QUE l'identification — pas d'enrôlement ici. Utilise
../models/enrolled_speakers.pkl généré par enrollment.py / auto_enroll.py.

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
(ça, ce serait très coûteux et inutile ici) — c'est une mise à jour du
"template" de référence de chaque conducteur, ce qui est l'équivalent
pratique pour ce genre de système par embeddings.

Historique de calibration (pour référence dans le rapport) :
    - ACCEPT_THRESHOLD initialement à 0.25 : un locuteur non-enrôlé
      (test avec un parent non-enrôlé) obtenait un score suffisant pour
      être accepté à tort. Remonté à 0.60 pour mieux rejeter les
      inconnus, au prix d'un risque légèrement accru de faux rejets sur
      des identifications correctes mais moins nettes (voix fatiguée,
      bruit ambiant, etc.) — à surveiller lors des tests suivants.
    - CONFIDENT_THRESHOLD remonté à 0.75 en cohérence, pour que
      l'adaptation incrémentale ne se déclenche que sur des
      identifications vraiment sans ambiguïté.
"""

import os
import pickle
import argparse

from common import load_model, get_embedding, get_embedding_from_array, cosine_similarity
from preferences import load_preferences, get_preferences, apply_preferences


# Seuil d'acceptation : en dessous, on rejette comme "INCONNU"
ACCEPT_THRESHOLD = 0.50

# Seuil de confiance pour déclencher l'adaptation : plus strict que
# ACCEPT_THRESHOLD, pour n'adapter que sur des cas très sûrs
CONFIDENT_THRESHOLD = 0.75

# Poids de la mise à jour (moyenne mobile). Petit = adaptation lente et
# prudente. À calibrer : 0.05-0.15 est un bon point de départ.
ADAPTATION_ALPHA = 0.1


def load_enrolled(path):
    with open(path, "rb") as f:
        return pickle.load(f)


def save_enrolled(speaker_embeddings, path):
    with open(path, "wb") as f:
        pickle.dump(speaker_embeddings, f)


def _score_and_decide(embedding, enrolled_speakers):
    """Logique de scoring partagée entre identify() (fichier) et identify_array() (streaming)."""
    scores = {
        name: cosine_similarity(embedding, ref_emb)
        for name, ref_emb in enrolled_speakers.items()
    }

    best_speaker = max(scores, key=scores.get)
    best_score = scores[best_speaker]

    if best_score < ACCEPT_THRESHOLD:
        return "INCONNU", best_score, scores

    return best_speaker, best_score, scores


def identify(classifier, audio_path, enrolled_speakers):
    """
    Retourne (speaker_name, best_score, all_scores, embedding).
    On retourne aussi l'embedding pour pouvoir l'utiliser dans
    l'adaptation sans le recalculer.
    """
    embedding = get_embedding(classifier, audio_path)
    speaker, best_score, scores = _score_and_decide(embedding, enrolled_speakers)
    return speaker, best_score, scores, embedding


def identify_array(classifier, audio_array, enrolled_speakers):
    """
    Identique à identify(), mais à partir d'un segment audio déjà en
    mémoire (np.array 1D) — utilisé par le streaming, pas de fichier
    intermédiaire nécessaire.
    """
    embedding = get_embedding_from_array(classifier, audio_array)
    speaker, best_score, scores = _score_and_decide(embedding, enrolled_speakers)
    return speaker, best_score, scores, embedding


def adapt_profile(enrolled_speakers, speaker_name, new_embedding, alpha=ADAPTATION_ALPHA):
    """
    Met à jour le profil d'un conducteur avec une moyenne mobile vers le
    nouvel embedding. Modifie enrolled_speakers en place.
    """
    old_embedding = enrolled_speakers[speaker_name]
    enrolled_speakers[speaker_name] = (1 - alpha) * old_embedding + alpha * new_embedding


def identify_and_adapt(classifier, audio_path, enrolled_speakers, save_path=None):
    """
    Identifie un échantillon (fichier), et adapte le profil du conducteur
    si la confiance est suffisante. Sauvegarde le fichier mis à jour si
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


def identify_and_adapt_array(classifier, audio_array, enrolled_speakers, save_path=None):
    """
    Équivalent de identify_and_adapt(), mais pour un segment audio déjà
    en mémoire (streaming). Mêmes règles d'adaptation.
    """
    speaker, score, scores, embedding = identify_array(classifier, audio_array, enrolled_speakers)

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
    parser.add_argument("--preferences", default="../preferences.json")
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

    if speaker != "INCONNU":
        all_preferences = load_preferences(args.preferences)
        driver_preferences = get_preferences(speaker, all_preferences)
        apply_preferences(speaker, driver_preferences)


if __name__ == "__main__":
    main()

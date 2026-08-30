"""
Enrôlement des conducteurs - script dédié
=============================================
À exécuter UNIQUEMENT quand tu ajoutes un nouveau conducteur, ou que tu
veux ré-enrôler quelqu'un avec de nouveaux échantillons. Le script
d'identification (identification.py) ne fait plus l'enrôlement — il se
contente de charger le fichier généré ici.

Usage :
    python3 enrollment.py                  # enrôle tout le monde depuis dataset/
    python3 enrollment.py --driver driver_3  # enrôle (ou ré-enrôle) un seul conducteur
"""

import os
import pickle
import argparse
import numpy as np

from common import load_model, get_embedding


def enroll_one_speaker(classifier, speaker_dir):
    """Calcule l'embedding moyen d'un conducteur à partir de ses fichiers .wav."""
    embeddings = []
    for fname in os.listdir(speaker_dir):
        if not fname.lower().endswith((".wav", ".flac")):
            continue
        fpath = os.path.join(speaker_dir, fname)
        embeddings.append(get_embedding(classifier, fpath))

    if not embeddings:
        return None

    return np.mean(embeddings, axis=0), len(embeddings)


def load_existing(path):
    if os.path.exists(path):
        with open(path, "rb") as f:
            return pickle.load(f)
    return {}


def save_enrolled(speaker_embeddings, path):
    os.makedirs(os.path.dirname(path) or ".", exist_ok=True)
    with open(path, "wb") as f:
        pickle.dump(speaker_embeddings, f)
    print(f"[OK] Sauvegardé dans {path} ({len(speaker_embeddings)} conducteurs)")


def main():
    parser = argparse.ArgumentParser(description="Enrôlement des conducteurs")
    parser.add_argument("--driver", default=None,
                         help="N'enrôler qu'un seul conducteur (nom du sous-dossier dans dataset/)")
    parser.add_argument("--dataset", default="../dataset")
    parser.add_argument("--output", default="../models/enrolled_speakers.pkl")
    parser.add_argument("--pretrained", default="../pretrained_ecapa")
    args = parser.parse_args()

    print("=== Chargement du modèle ECAPA-TDNN ===")
    classifier = load_model(savedir=args.pretrained)

    print("=== Chargement des conducteurs déjà enrôlés ===")
    enrolled = load_existing(args.output)
    print(f"Déjà enrôlés : {list(enrolled.keys()) or 'aucun'}")

    speakers_to_process = (
        [args.driver] if args.driver
        else sorted(d for d in os.listdir(args.dataset) if os.path.isdir(os.path.join(args.dataset, d)))
    )

    print("\n=== Enrôlement ===")
    for speaker_name in speakers_to_process:
        speaker_dir = os.path.join(args.dataset, speaker_name)
        if not os.path.isdir(speaker_dir):
            print(f"[!] Dossier introuvable pour '{speaker_name}', ignoré.")
            continue

        result = enroll_one_speaker(classifier, speaker_dir)
        if result is None:
            print(f"[!] Aucun échantillon trouvé pour '{speaker_name}', ignoré.")
            continue

        embedding, n_samples = result
        is_update = speaker_name in enrolled
        enrolled[speaker_name] = embedding
        action = "Ré-enrôlé" if is_update else "Enrôlé"
        print(f"[OK] {action} : '{speaker_name}' ({n_samples} échantillons)")

    save_enrolled(enrolled, args.output)


if __name__ == "__main__":
    main()

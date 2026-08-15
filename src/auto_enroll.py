"""
Enrôlement automatique via micro
====================================
Enregistre automatiquement des échantillons vocaux au micro (avec la
même détection de parole que le streaming), les sauvegarde en .wav dans
dataset/<driver_name>/, puis lance l'enrôlement — plus besoin de créer
les fichiers .wav à la main.

Ce script a besoin d'un vrai micro physique (ne fonctionne pas dans un
Codespace).

Usage :
    python3 auto_enroll.py driver_3                  # enregistre 5 échantillons (défaut)
    python3 auto_enroll.py driver_3 --samples 8       # enregistre 8 échantillons

Dépendances :
    pip install sounddevice soundfile numpy torch speechbrain librosa --break-system-packages
"""

import os
import argparse
import soundfile as sf

from common import load_model
from audio_capture import speech_segments, SAMPLE_RATE, MIN_SPEECH_SECONDS
from enrollment import enroll_one_speaker, load_existing, save_enrolled


def record_samples(driver_name, dataset_dir, n_samples):
    """
    Enregistre n_samples segments de parole au micro et les sauvegarde
    en .wav dans dataset_dir/driver_name/. Ne touche pas aux échantillons
    déjà présents (numérotation qui continue).
    """
    speaker_dir = os.path.join(dataset_dir, driver_name)
    os.makedirs(speaker_dir, exist_ok=True)

    existing = [f for f in os.listdir(speaker_dir) if f.lower().endswith(".wav")]
    start_index = len(existing)

    print(f"\n=== Enregistrement pour '{driver_name}' ===")
    print(f"Cible : {n_samples} échantillons (~{MIN_SPEECH_SECONDS:.0f}s min chacun).")
    print("Parle normalement, une phrase à la fois, avec une pause entre chaque.")
    print("(Ctrl+C pour arrêter avant d'avoir atteint le nombre cible)\n")

    collected = 0
    try:
        for segment in speech_segments():
            idx = start_index + collected
            path = os.path.join(speaker_dir, f"sample_{idx}.wav")
            sf.write(path, segment, SAMPLE_RATE)
            collected += 1
            duration = len(segment) / SAMPLE_RATE
            print(f"[OK] Échantillon {collected}/{n_samples} enregistré "
                  f"({duration:.1f}s) -> {path}")

            if collected >= n_samples:
                break
    except KeyboardInterrupt:
        print(f"\n[!] Arrêté manuellement après {collected} échantillon(s).")

    return speaker_dir, collected


def main():
    parser = argparse.ArgumentParser(description="Enrôlement automatique via micro")
    parser.add_argument("driver_name", help="Nom du conducteur (= nom du dossier créé dans dataset/)")
    parser.add_argument("--samples", type=int, default=5,
                         help="Nombre d'échantillons vocaux à enregistrer (défaut: 5)")
    parser.add_argument("--dataset", default="../dataset")
    parser.add_argument("--output", default="../models/enrolled_speakers.pkl")
    parser.add_argument("--pretrained", default="../pretrained_ecapa")
    args = parser.parse_args()

    speaker_dir, collected = record_samples(args.driver_name, args.dataset, args.samples)

    if collected == 0:
        print("[!] Aucun échantillon enregistré, enrôlement annulé.")
        return

    print("\n=== Chargement du modèle ECAPA-TDNN ===")
    classifier = load_model(savedir=args.pretrained)

    print("=== Enrôlement à partir des échantillons enregistrés ===")
    enrolled = load_existing(args.output)

    result = enroll_one_speaker(classifier, speaker_dir)
    if result is None:
        print(f"[!] Échec de l'enrôlement pour '{args.driver_name}'.")
        return

    embedding, n_total_samples = result
    is_update = args.driver_name in enrolled
    enrolled[args.driver_name] = embedding
    save_enrolled(enrolled, args.output)

    action = "Ré-enrôlé" if is_update else "Enrôlé"
    print(f"\n[OK] {action} : '{args.driver_name}' "
          f"({n_total_samples} échantillons au total dans son dossier)")


if __name__ == "__main__":
    main()

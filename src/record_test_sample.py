"""
Enregistrement d'un échantillon de test propre
====================================================
Utilise le même pipeline de capture + VAD que l'enrôlement
(audio_capture.py), pour garantir que les fichiers de test ont la même
qualité que les fichiers d'enrôlement (pas de silence excessif, pas de
saturation, durée cohérente). Évite le biais qu'on a eu en enregistrant
les tests "à la main" avec Audacity.

Usage :
    python3 record_test_sample.py test_1
    python3 record_test_sample.py inconnu_ami
"""

import sys
import os
import soundfile as sf

from audio_capture import speech_segments, SAMPLE_RATE


def main():
    if len(sys.argv) < 2:
        print("Usage : python3 record_test_sample.py <nom_du_fichier_sans_extension>")
        sys.exit(1)

    name = sys.argv[1]
    output_dir = "../test_samples"
    os.makedirs(output_dir, exist_ok=True)
    output_path = os.path.join(output_dir, f"{name}.wav")

    print("Parle normalement pendant quelques secondes, puis fais une pause...")
    print("(le premier segment de parole détecté sera sauvegardé)\n")

    for segment in speech_segments():
        sf.write(output_path, segment, SAMPLE_RATE)
        duration = len(segment) / SAMPLE_RATE
        print(f"[OK] Échantillon enregistré ({duration:.1f}s) -> {output_path}")
        break  # un seul segment suffit pour un test


if __name__ == "__main__":
    main()

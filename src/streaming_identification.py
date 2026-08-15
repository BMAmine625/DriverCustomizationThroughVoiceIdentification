"""
Identification du locuteur sur flux audio continu (version scindée)
========================================================================
Version refactorisée : réutilise common.py, identification.py, et
audio_capture.py au lieu de dupliquer la logique. Ne fait AUCUN
enrôlement — uniquement de l'identification en direct sur ce qui est
déjà dans ../models/enrolled_speakers.pkl.

Ce script a besoin d'un vrai micro physique — il ne fonctionne pas dans
un Codespace ou un environnement sans accès matériel.

Dépendances :
    pip install sounddevice numpy torch speechbrain librosa --break-system-packages
"""

import time
import threading

from common import load_model
from identification import load_enrolled, identify_and_adapt_array
from audio_capture import speech_segments


def wait_for_quit(stop_event):
    """Tourne dans un thread séparé : attend que l'utilisateur tape 'q' + Entrée."""
    while not stop_event.is_set():
        try:
            typed = input()
        except EOFError:
            return
        if typed.strip().lower() == "q":
            stop_event.set()
            return


def run_stream(classifier, enrolled_speakers, save_path):
    print("=== Écoute en continu ===")
    print("Tape 'q' puis Entrée pour arrêter (ou Ctrl+C).\n")

    stop_event = threading.Event()
    listener = threading.Thread(target=wait_for_quit, args=(stop_event,), daemon=True)
    listener.start()

    try:
        for segment in speech_segments(stop_event=stop_event):
            speaker, score, scores, adapted = identify_and_adapt_array(
                classifier, segment, enrolled_speakers, save_path=save_path
            )

            print(f"\n[{time.strftime('%H:%M:%S')}] Conducteur détecté : {speaker} (score: {score:.3f})")
            for name, s in sorted(scores.items(), key=lambda x: -x[1]):
                print(f"    {name}: {s:.3f}")

            if adapted:
                print(f"    [OK] Profil de '{speaker}' mis à jour (identification très confiante)")

    except KeyboardInterrupt:
        pass
    finally:
        stop_event.set()
        print("\n=== Arrêt de l'écoute ===")


if __name__ == "__main__":
    MODEL_SAVE_PATH = "../models/enrolled_speakers.pkl"
    PRETRAINED_DIR = "../pretrained_ecapa"

    print("=== Chargement du modèle ECAPA-TDNN ===")
    classifier = load_model(savedir=PRETRAINED_DIR)

    print("=== Chargement des conducteurs enrôlés ===")
    enrolled_speakers = load_enrolled(MODEL_SAVE_PATH)
    print(f"Conducteurs chargés : {list(enrolled_speakers.keys())}")

    run_stream(classifier, enrolled_speakers, save_path=MODEL_SAVE_PATH)

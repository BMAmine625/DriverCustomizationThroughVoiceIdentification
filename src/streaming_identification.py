"""
Identification du locuteur sur flux audio continu (version scindée)
========================================================================
Réutilise common.py, identification.py, et audio_capture.py au lieu de
dupliquer la logique. Ne fait AUCUN enrôlement — uniquement de
l'identification en direct sur ce qui est déjà dans
../models/enrolled_speakers.pkl.

Ce script a besoin d'un vrai micro physique — il ne fonctionne pas dans
un Codespace ou un environnement sans accès matériel.

Dépendances :
    pip install sounddevice numpy torch speechbrain librosa --break-system-packages
"""

import time
import argparse
import threading

from common import load_model
from identification import load_enrolled, identify_and_adapt_array, identify_array
from audio_capture import speech_segments
from preferences import load_preferences, get_preferences, apply_preferences


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


def run_stream(classifier, enrolled_speakers, save_path, adapt=True, preferences_path="../preferences.json"):
    print("=== Écoute en continu ===")
    print("Tape 'q' puis Entrée pour arrêter (ou Ctrl+C).")
    if not adapt:
        print("(mode --no-adapt : les profils ne seront PAS modifiés)")
    print()

    all_preferences = load_preferences(preferences_path)
    current_driver = None  # pour n'appliquer les préférences qu'au changement de conducteur

    stop_event = threading.Event()
    listener = threading.Thread(target=wait_for_quit, args=(stop_event,), daemon=True)
    listener.start()

    try:
        for segment in speech_segments(stop_event=stop_event):
            if adapt:
                speaker, score, scores, adapted = identify_and_adapt_array(
                    classifier, segment, enrolled_speakers, save_path=save_path
                )
            else:
                speaker, score, scores, _ = identify_array(classifier, segment, enrolled_speakers)
                adapted = False

            print(f"\n[{time.strftime('%H:%M:%S')}] Conducteur détecté : {speaker} (score: {score:.3f})")
            for name, s in sorted(scores.items(), key=lambda x: -x[1]):
                print(f"    {name}: {s:.3f}")

            if adapted:
                print(f"    [OK] Profil de '{speaker}' mis à jour (identification très confiante)")

            if speaker != "INCONNU" and speaker != current_driver:
                driver_preferences = get_preferences(speaker, all_preferences)
                apply_preferences(speaker, driver_preferences)
                current_driver = speaker

    except KeyboardInterrupt:
        pass
    finally:
        stop_event.set()
        print("\n=== Arrêt de l'écoute ===")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Identification en continu depuis le micro")
    parser.add_argument("--no-adapt", action="store_true",
                         help="Désactive l'adaptation incrémentale (utile pour calibrer les seuils)")
    parser.add_argument("--models", default="../models/enrolled_speakers.pkl")
    parser.add_argument("--pretrained", default="../pretrained_ecapa")
    parser.add_argument("--preferences", default="../preferences.json")
    args = parser.parse_args()

    print("=== Chargement du modèle ECAPA-TDNN ===")
    classifier = load_model(savedir=args.pretrained)

    print("=== Chargement des conducteurs enrôlés ===")
    enrolled_speakers = load_enrolled(args.models)
    print(f"Conducteurs chargés : {list(enrolled_speakers.keys())}")

    run_stream(classifier, enrolled_speakers, save_path=args.models,
               adapt=not args.no_adapt, preferences_path=args.preferences)

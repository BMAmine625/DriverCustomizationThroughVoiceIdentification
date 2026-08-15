"""
Capture micro continue avec détection de parole (VAD)
=========================================================
Module partagé par streaming_identification.py (identification en
direct) et auto_enroll.py (enrôlement automatique) — la même logique
de segmentation de la parole est utilisée dans les deux cas, pour
garantir que les segments produits pendant l'enrôlement et pendant
l'identification sont cohérents (même durée min/max, même VAD).
"""

import queue
import numpy as np
import sounddevice as sd

SAMPLE_RATE = 16000
BLOCK_DURATION = 0.03          # 30 ms par bloc capturé
BLOCK_SIZE = int(SAMPLE_RATE * BLOCK_DURATION)

MIN_SPEECH_SECONDS = 2.0       # durée min de parole avant de clôturer un segment
MAX_SPEECH_SECONDS = 4.0       # durée max d'un segment (sécurité)
SILENCE_TIMEOUT = 0.6          # secondes de silence qui closent un segment

ENERGY_THRESHOLD = 0.01        # seuil RMS parole/silence — à calibrer selon
                                # ton micro et le bruit ambiant (ex. bruit moteur)

QUEUE_TIMEOUT = 0.2            # timeout court sur l'attente de bloc audio, pour
                                # que Ctrl+C (KeyboardInterrupt) soit pris en compte
                                # rapidement au lieu de bloquer indéfiniment


def is_speech(block, threshold=ENERGY_THRESHOLD):
    rms = np.sqrt(np.mean(block ** 2))
    return rms > threshold


def speech_segments(stop_event=None):
    """
    Générateur infini : capture le micro et "yield" un segment de parole
    (np.array 1D, float32, à SAMPLE_RATE) à chaque fois qu'une phrase
    complète est détectée.

    stop_event : threading.Event optionnel. Si fourni et déclenché
    (stop_event.set()), le générateur s'arrête proprement et ferme le
    flux micro, au lieu de compter uniquement sur Ctrl+C.

    Usage :
        for segment in speech_segments():
            ... traiter le segment ...
            if condition_arret:
                break   # ferme proprement le flux micro
    """
    block_queue = queue.Queue()

    def callback(indata, frames, time_info, status):
        block_queue.put(indata[:, 0].copy())

    speech_buffer = []
    silence_duration = 0.0

    with sd.InputStream(
        samplerate=SAMPLE_RATE,
        channels=1,
        blocksize=BLOCK_SIZE,
        callback=callback,
    ):
        while True:
            if stop_event is not None and stop_event.is_set():
                return  # ferme le "with" proprement (flux micro coupé) et termine le générateur

            try:
                block = block_queue.get(timeout=QUEUE_TIMEOUT)
            except queue.Empty:
                continue  # pas de nouveau bloc, on reboucle (et Ctrl+C/stop_event sont vérifiés ici)

            if is_speech(block):
                speech_buffer.append(block)
                silence_duration = 0.0
            else:
                if speech_buffer:
                    silence_duration += BLOCK_DURATION

            total_speech_duration = len(speech_buffer) * BLOCK_DURATION

            should_yield = speech_buffer and (
                (silence_duration >= SILENCE_TIMEOUT and total_speech_duration >= MIN_SPEECH_SECONDS)
                or total_speech_duration >= MAX_SPEECH_SECONDS
            )

            if should_yield:
                segment = np.concatenate(speech_buffer)
                speech_buffer = []
                silence_duration = 0.0
                yield segment

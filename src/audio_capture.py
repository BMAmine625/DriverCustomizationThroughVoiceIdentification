"""
Capture micro continue avec détection de parole (VAD)
=========================================================
Module partagé par streaming_identification.py, auto_enroll.py, et
api_server.py — la même logique de segmentation de la parole est
utilisée partout, pour garantir des segments cohérents.
"""

import queue
import numpy as np
import librosa
import sounddevice as sd

SAMPLE_RATE = 16000            # taux attendu par le modèle (ECAPA-TDNN)
BLOCK_DURATION = 0.03          # 30 ms par bloc capturé
BLOCK_SIZE = int(SAMPLE_RATE * BLOCK_DURATION)

MIN_SPEECH_SECONDS = 2.0       # durée min de parole avant de clôturer un segment
MAX_SPEECH_SECONDS = 4.0       # durée max d'un segment (sécurité)
SILENCE_TIMEOUT = 0.6          # secondes de silence qui closent un segment

ENERGY_THRESHOLD = 0.01        # seuil RMS parole/silence — à calibrer selon
                                # ton micro et le bruit ambiant (ex. bruit moteur)

QUEUE_TIMEOUT = 0.2            # timeout court sur l'attente de bloc audio, pour
                                # que Ctrl+C (KeyboardInterrupt) et stop_event
                                # soient pris en compte rapidement


def is_speech(block, threshold=ENERGY_THRESHOLD):
    rms = np.sqrt(np.mean(block ** 2))
    return rms > threshold


def _native_input_samplerate() -> int:
    """Taux d'échantillonnage natif du micro d'entrée par défaut.

    Certains micros USB bon marché refusent d'être ouverts directement
    à 16 kHz (PaErrorCode -9997, "Invalid sample rate") : leur matériel
    n'accepte qu'un taux fixe (souvent 44100 ou 48000 Hz), et sur un Pi
    "Lite" sans PulseAudio/PipeWire, il n'y a pas de conversion logicielle
    automatique en amont. On interroge donc le taux réellement supporté
    et on capture à ce taux, puis on ré-échantillonne vers SAMPLE_RATE
    juste avant de renvoyer le segment (voir speech_segments ci-dessous).
    """
    device_info = sd.query_devices(kind="input")
    return int(device_info["default_samplerate"])


def speech_segments(stop_event=None):
    """
    Générateur infini : capture le micro et "yield" un segment de parole
    (np.array 1D, float32, à SAMPLE_RATE) à chaque fois qu'une phrase
    complète est détectée.

    stop_event : threading.Event optionnel. Si fourni et déclenché
    (stop_event.set()), le générateur s'arrête proprement et ferme le
    flux micro.

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

    native_rate = _native_input_samplerate()
    native_block_size = int(native_rate * BLOCK_DURATION)

    with sd.InputStream(
        samplerate=native_rate,
        channels=1,
        blocksize=native_block_size,
        callback=callback,
    ):
        while True:
            if stop_event is not None and stop_event.is_set():
                return

            try:
                block = block_queue.get(timeout=QUEUE_TIMEOUT)
            except queue.Empty:
                continue

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
                if native_rate != SAMPLE_RATE:
                    segment = librosa.resample(
                        segment, orig_sr=native_rate, target_sr=SAMPLE_RATE
                    )
                yield segment
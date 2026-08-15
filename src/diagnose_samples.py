"""
Diagnostic rapide de la qualité des échantillons audio
==========================================================
Vérifie durée, volume (RMS), et niveau de silence de chaque fichier
.wav d'un conducteur — utile pour repérer des enregistrements trop
courts, trop faibles, ou coupés par le VAD.

Usage :
    python3 diagnose_samples.py ../dataset/driver_1
    python3 diagnose_samples.py ../dataset/driver_2
"""

import sys
import os
import numpy as np
import librosa


def diagnose_file(path):
    signal, sr = librosa.load(path, sr=16000, mono=True)
    duration = len(signal) / sr
    rms = np.sqrt(np.mean(signal ** 2))
    peak = np.max(np.abs(signal))

    # % du signal considéré comme "silence" (RMS local très faible)
    frame_length = int(0.03 * sr)  # 30ms, comme le VAD
    n_frames = len(signal) // frame_length
    silent_frames = 0
    for i in range(n_frames):
        frame = signal[i * frame_length:(i + 1) * frame_length]
        if np.sqrt(np.mean(frame ** 2)) < 0.01:
            silent_frames += 1
    silence_pct = 100 * silent_frames / max(n_frames, 1)

    return {
        "duration": duration,
        "rms": rms,
        "peak": peak,
        "silence_pct": silence_pct,
    }


def main():
    if len(sys.argv) < 2:
        print("Usage : python3 diagnose_samples.py <dossier_conducteur>")
        sys.exit(1)

    speaker_dir = sys.argv[1]
    files = sorted(f for f in os.listdir(speaker_dir) if f.lower().endswith((".wav", ".flac")))

    if not files:
        print(f"Aucun fichier audio trouvé dans {speaker_dir}")
        sys.exit(1)

    print(f"=== Diagnostic de {speaker_dir} ({len(files)} fichiers) ===\n")
    print(f"{'Fichier':<20} {'Durée':>8} {'RMS':>10} {'Pic':>8} {'Silence %':>10}")
    print("-" * 60)

    for fname in files:
        path = os.path.join(speaker_dir, fname)
        d = diagnose_file(path)
        print(f"{fname:<20} {d['duration']:>6.2f}s {d['rms']:>10.4f} "
              f"{d['peak']:>8.3f} {d['silence_pct']:>9.1f}%")

    print("\nRepères pour interpréter :")
    print("  - Durée : idéalement 2-4s minimum par fichier")
    print("  - RMS   : trop faible (<0.02) = voix trop discrète / micro loin")
    print("  - Pic   : proche de 1.0 = risque de saturation (clipping)")
    print("  - Silence % : >40% = beaucoup de silence capturé, peu de vraie parole")


if __name__ == "__main__":
    main()

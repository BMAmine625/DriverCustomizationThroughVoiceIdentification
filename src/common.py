"""
Fonctions partagées - ECAPA-TDNN
===================================
Code commun utilisé par enrollment.py, identification.py, api_server.py,
etc., pour éviter la duplication et garantir que l'extraction
d'embedding est identique partout.
"""

import torch
import librosa
import numpy as np
from speechbrain.inference.speaker import EncoderClassifier


def load_model(savedir="../pretrained_ecapa"):
    """Charge (et met en cache) le modèle ECAPA-TDNN pré-entraîné."""
    return EncoderClassifier.from_hparams(
        source="speechbrain/spkrec-ecapa-voxceleb",
        savedir=savedir,
        run_opts={"device": "cpu"},
    )


def get_embedding(classifier, audio_path, target_sr=16000):
    """
    Charge un fichier audio (via librosa, pas de dépendance FFmpeg/torchcodec)
    et retourne son embedding (vecteur 192-d).
    """
    signal, sr = librosa.load(audio_path, sr=target_sr, mono=True)
    signal = torch.from_numpy(signal).float().unsqueeze(0)

    with torch.no_grad():
        embedding = classifier.encode_batch(signal)

    return embedding.squeeze().numpy()


def get_embedding_from_array(classifier, audio_array, target_sr=16000):
    """
    Même chose que get_embedding, mais à partir d'un tableau numpy déjà
    en mémoire (utile pour le streaming, pas besoin de fichier intermédiaire).
    """
    signal = torch.from_numpy(audio_array).float().unsqueeze(0)

    with torch.no_grad():
        embedding = classifier.encode_batch(signal)

    return embedding.squeeze().numpy()


def cosine_similarity(a, b):
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-8))

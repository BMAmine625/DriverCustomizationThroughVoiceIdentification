"""
Système d'identification du locuteur - Baseline MFCC + GMM
=============================================================
Approche classique : chaque conducteur est modélisé par un GMM entraîné
sur les MFCC extraits de ses échantillons vocaux. L'identification se
fait en calculant la vraisemblance (log-likelihood) de chaque modèle
GMM sur un nouvel échantillon, et en choisissant le locuteur dont le
modèle donne la meilleure vraisemblance.

Dépendances :
    pip install librosa scikit-learn numpy soundfile --break-system-packages

Organisation attendue des données :
    dataset/
        driver_1/
            sample1.wav
            sample2.wav
            ...
        driver_2/
            sample1.wav
            ...
"""

import os
import pickle
import numpy as np
import librosa
from sklearn.mixture import GaussianMixture


# ----------------------------------------------------------------------
# 1. Extraction des caractéristiques (MFCC + delta + delta-delta)
# ----------------------------------------------------------------------
def extract_features(audio_path, n_mfcc=13, sr=16000):
    """
    Extrait les MFCC d'un fichier audio, avec leurs dérivées première
    et seconde (delta, delta-delta) pour capturer la dynamique du signal.

    Retourne un tableau de forme (n_frames, n_mfcc*3).
    """
    signal, sample_rate = librosa.load(audio_path, sr=sr)

    # Retire les silences en début/fin (améliore la robustesse)
    signal, _ = librosa.effects.trim(signal, top_db=25)

    mfcc = librosa.feature.mfcc(y=signal, sr=sample_rate, n_mfcc=n_mfcc)
    delta = librosa.feature.delta(mfcc)
    delta2 = librosa.feature.delta(mfcc, order=2)

    features = np.concatenate([mfcc, delta, delta2], axis=0)  # (n_mfcc*3, n_frames)

    # Normalisation par caractéristique (moyenne 0, variance 1)
    features = (features - features.mean(axis=1, keepdims=True)) / (
        features.std(axis=1, keepdims=True) + 1e-8
    )

    return features.T  # (n_frames, n_mfcc*3)


# ----------------------------------------------------------------------
# 2. Entraînement d'un GMM par conducteur
# ----------------------------------------------------------------------
def train_speaker_models(dataset_dir, n_components=16):
    """
    Parcourt dataset_dir/<speaker_name>/*.wav, extrait les features de
    tous les échantillons d'un locuteur, et entraîne un GMM dessus.

    Retourne un dict {speaker_name: GaussianMixture}.
    """
    models = {}

    for speaker_name in sorted(os.listdir(dataset_dir)):
        speaker_dir = os.path.join(dataset_dir, speaker_name)
        if not os.path.isdir(speaker_dir):
            continue

        all_features = []
        for fname in os.listdir(speaker_dir):
            if not fname.lower().endswith((".wav", ".flac")):
                continue
            fpath = os.path.join(speaker_dir, fname)
            feats = extract_features(fpath)
            all_features.append(feats)

        if not all_features:
            print(f"[!] Aucun échantillon trouvé pour {speaker_name}, ignoré.")
            continue

        X = np.vstack(all_features)

        gmm = GaussianMixture(
            n_components=n_components,
            covariance_type="diag",  # diagonale = plus rapide, standard pour MFCC
            max_iter=200,
            n_init=3,
            random_state=42,
        )
        gmm.fit(X)
        models[speaker_name] = gmm
        print(f"[OK] Modèle GMM entraîné pour '{speaker_name}' "
              f"({X.shape[0]} frames, {len(all_features)} fichiers)")

    return models


# ----------------------------------------------------------------------
# 3. Identification d'un nouvel échantillon
# ----------------------------------------------------------------------
def identify_speaker(audio_path, models, threshold=None):
    """
    Calcule la log-vraisemblance moyenne de l'échantillon pour chaque
    modèle GMM, et retourne le locuteur le plus probable.

    threshold : si fourni, rejette l'identification si le meilleur score
                est en dessous de ce seuil (utile pour détecter un
                conducteur inconnu / non enrôlé).
    """
    features = extract_features(audio_path)

    scores = {}
    for speaker_name, gmm in models.items():
        log_likelihood = gmm.score(features)  # moyenne par frame
        scores[speaker_name] = log_likelihood

    best_speaker = max(scores, key=scores.get)
    best_score = scores[best_speaker]

    if threshold is not None and best_score < threshold:
        return "INCONNU", scores

    return best_speaker, scores


# ----------------------------------------------------------------------
# 4. Sauvegarde / chargement des modèles (pour déploiement embarqué)
# ----------------------------------------------------------------------
def save_models(models, path="speaker_models.pkl"):
    with open(path, "wb") as f:
        pickle.dump(models, f)
    print(f"[OK] Modèles sauvegardés dans {path}")


def load_models(path="speaker_models.pkl"):
    with open(path, "rb") as f:
        return pickle.load(f)


# ----------------------------------------------------------------------
# Exemple d'utilisation
# ----------------------------------------------------------------------
if __name__ == "__main__":
    # Chemins relatifs à src/, cohérents avec la structure du projet :
    #   voice_driver_id/dataset/, /test_samples/, /models/, /src/
    DATASET_DIR = "../dataset"                     # un sous-dossier par conducteur
    TEST_FILE = "../test_samples/test_he.wav"  # échantillon à identifier
    MODEL_SAVE_PATH = "../models/speaker_models.pkl"   # où sauvegarder le modèle entraîné

    print("=== Entraînement des modèles GMM ===")
    models = train_speaker_models(DATASET_DIR, n_components=2)
    save_models(models, path=MODEL_SAVE_PATH)

    print("\n=== Test d'identification ===")
    # Seuil à calibrer expérimentalement sur ton propre dataset
    # (calcule les scores sur des échantillons connus vs inconnus pour le fixer)
    speaker, scores = identify_speaker(TEST_FILE, models, threshold=-60.0)

    print(f"Conducteur identifié : {speaker}")
    print("Scores détaillés (log-vraisemblance) :")
    for name, score in sorted(scores.items(), key=lambda x: -x[1]):
        print(f"  {name}: {score:.2f}")

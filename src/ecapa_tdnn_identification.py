"""
Système d'identification du locuteur - ECAPA-TDNN (SpeechBrain)
==================================================================
Approche deep learning : on utilise un modèle ECAPA-TDNN pré-entraîné
sur VoxCeleb (via SpeechBrain) pour extraire un "embedding" (vecteur
caractéristique) de chaque échantillon vocal. L'identification se fait
par similarité cosinus entre l'embedding du nouvel échantillon et les
embeddings de référence de chaque conducteur enrôlé.

Avantage par rapport au GMM+MFCC : pas besoin d'entraîner un modèle
from scratch, juste "enrôler" quelques échantillons par conducteur.

Dépendances :
    pip install speechbrain torch librosa soundfile --break-system-packages
    (torchaudio n'est plus nécessaire pour le chargement audio ici -
     on utilise librosa pour éviter la dépendance torchcodec/FFmpeg)

Note pour le déploiement embarqué (STM32MP/RPi) :
    Le modèle complet (~20 Mo) peut être trop lourd pour de l'inférence
    temps réel sur cible. Deux options :
      1. Utiliser torch.quantization pour passer le modèle en int8
      2. Exporter en ONNX puis utiliser ONNX Runtime (souvent plus rapide
         et plus portable sur ARM que PyTorch complet)
    Voir la section "portage embarqué" en bas de fichier.

Organisation attendue des données :
    dataset/
        driver_1/
            sample1.wav
            sample2.wav
        driver_2/
            sample1.wav
            ...
"""

import os
import pickle
import numpy as np
import torch
import librosa
from speechbrain.inference.speaker import EncoderClassifier


# ----------------------------------------------------------------------
# 1. Chargement du modèle pré-entraîné
# ----------------------------------------------------------------------
def load_model(savedir="pretrained_ecapa"):
    """
    Télécharge (première fois) et charge le modèle ECAPA-TDNN
    pré-entraîné sur VoxCeleb. Le modèle est mis en cache dans savedir.
    """
    classifier = EncoderClassifier.from_hparams(
        source="speechbrain/spkrec-ecapa-voxceleb",
        savedir=savedir,
        run_opts={"device": "cpu"},  # passer à "cuda" si GPU dispo pour l'entraînement/dev
    )
    return classifier


# ----------------------------------------------------------------------
# 2. Extraction d'un embedding pour un fichier audio
# ----------------------------------------------------------------------
def get_embedding(classifier, audio_path, target_sr=16000):
    """
    Charge un fichier audio avec librosa (mono, rééchantillonné à 16kHz
    directement), et retourne son embedding (vecteur 192-d).

    Note : on utilise librosa plutôt que torchaudio.load() ici, car ce
    dernier dépend de torchcodec + FFmpeg installé au niveau système,
    ce qui pose souvent problème dans des environnements comme les
    GitHub Codespaces. librosa gère le chargement + resampling en une
    seule étape, sans dépendance système supplémentaire.
    """
    signal, sr = librosa.load(audio_path, sr=target_sr, mono=True)

    # librosa retourne un tableau numpy 1D -> tensor PyTorch (1, n_samples)
    signal = torch.from_numpy(signal).float().unsqueeze(0)

    with torch.no_grad():
        embedding = classifier.encode_batch(signal)

    return embedding.squeeze().numpy()  # vecteur (192,)


# ----------------------------------------------------------------------
# 3. Enrôlement des conducteurs (moyenne des embeddings par locuteur)
# ----------------------------------------------------------------------
def enroll_speakers(classifier, dataset_dir):
    """
    Parcourt dataset_dir/<speaker_name>/*.wav, calcule l'embedding moyen
    de chaque conducteur à partir de ses échantillons d'enrôlement.

    Retourne un dict {speaker_name: embedding_moyen (np.array)}.
    """
    speaker_embeddings = {}

    for speaker_name in sorted(os.listdir(dataset_dir)):
        speaker_dir = os.path.join(dataset_dir, speaker_name)
        if not os.path.isdir(speaker_dir):
            continue

        embeddings = []
        for fname in os.listdir(speaker_dir):
            if not fname.lower().endswith((".wav", ".flac")):
                continue
            fpath = os.path.join(speaker_dir, fname)
            emb = get_embedding(classifier, fpath)
            embeddings.append(emb)

        if not embeddings:
            print(f"[!] Aucun échantillon trouvé pour {speaker_name}, ignoré.")
            continue

        mean_embedding = np.mean(embeddings, axis=0)
        speaker_embeddings[speaker_name] = mean_embedding
        print(f"[OK] Conducteur '{speaker_name}' enrôlé "
              f"({len(embeddings)} échantillons)")

    return speaker_embeddings


# ----------------------------------------------------------------------
# 4. Identification par similarité cosinus
# ----------------------------------------------------------------------
def cosine_similarity(a, b):
    return float(np.dot(a, b) / (np.linalg.norm(a) * np.linalg.norm(b) + 1e-8))


def identify_speaker(classifier, audio_path, enrolled_speakers, threshold=0.25):
    """
    Compare l'embedding du nouvel échantillon à chaque conducteur enrôlé.
    threshold : seuil de similarité cosinus en dessous duquel on
                considère que le locuteur n'est pas reconnu (à calibrer
                sur ton propre dataset — typiquement entre 0.2 et 0.4).
    """
    test_embedding = get_embedding(classifier, audio_path)

    scores = {
        name: cosine_similarity(test_embedding, ref_emb)
        for name, ref_emb in enrolled_speakers.items()
    }

    best_speaker = max(scores, key=scores.get)
    best_score = scores[best_speaker]

    if best_score < threshold:
        return "INCONNU", scores

    return best_speaker, scores


# ----------------------------------------------------------------------
# 5. Sauvegarde / chargement des embeddings enrôlés
# ----------------------------------------------------------------------
def save_enrolled_speakers(speaker_embeddings, path="enrolled_speakers.pkl"):
    with open(path, "wb") as f:
        pickle.dump(speaker_embeddings, f)
    print(f"[OK] Embeddings sauvegardés dans {path}")


def load_enrolled_speakers(path="enrolled_speakers.pkl"):
    with open(path, "rb") as f:
        return pickle.load(f)


# ----------------------------------------------------------------------
# Exemple d'utilisation
# ----------------------------------------------------------------------
if __name__ == "__main__":
    # Chemins relatifs à src/, cohérents avec la structure du projet :
    #   voice_driver_id/dataset/, /test_samples/, /models/, /src/, /pretrained_ecapa/
    DATASET_DIR = "../dataset"
    TEST_FILE = "../test_samples/test_he.wav"
    MODEL_SAVE_PATH = "../models/enrolled_speakers.pkl"
    PRETRAINED_DIR = "../pretrained_ecapa"  # cache du modèle téléchargé par SpeechBrain

    print("=== Chargement du modèle ECAPA-TDNN pré-entraîné ===")
    classifier = load_model(savedir=PRETRAINED_DIR)

    print("\n=== Enrôlement des conducteurs ===")
    enrolled = enroll_speakers(classifier, DATASET_DIR)
    save_enrolled_speakers(enrolled, path=MODEL_SAVE_PATH)

    print("\n=== Test d'identification ===")
    speaker, scores = identify_speaker(classifier, TEST_FILE, enrolled, threshold=0.25)

    print(f"Conducteur identifié : {speaker}")
    print("Scores détaillés (similarité cosinus) :")
    for name, score in sorted(scores.items(), key=lambda x: -x[1]):
        print(f"  {name}: {score:.3f}")

    # ------------------------------------------------------------------
    # Portage embarqué (à exécuter séparément une fois le prototype validé)
    # ------------------------------------------------------------------
    # import torch
    # quantized_model = torch.quantization.quantize_dynamic(
    #     classifier.mods.embedding_model, {torch.nn.Linear}, dtype=torch.qint8
    # )
    # torch.save(quantized_model.state_dict(), "ecapa_quantized.pt")
    #
    # Pour un export ONNX (souvent préférable sur cible ARM) :
    # dummy_input = torch.randn(1, 16000)  # ~1s audio à 16kHz
    # torch.onnx.export(classifier.mods.embedding_model, dummy_input, "ecapa.onnx")

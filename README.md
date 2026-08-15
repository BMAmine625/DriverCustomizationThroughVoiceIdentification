# Système de reconnaissance vocale pour personnalisation des préférences conducteur

Identification du conducteur par sa voix, pour charger automatiquement ses
préférences véhicule (siège, volant, climatisation, rétroviseurs).

## Installation

Une seule commande, depuis la racine du projet :

```bash
./setup.sh
```

Ce script crée un environnement virtuel Python, installe `torch`/`torchaudio`
en version CPU (nécessaire pour éviter des conflits CUDA), puis toutes les
autres dépendances.

Une fois terminé, active l'environnement à chaque nouvelle session de travail :

```bash
source venv/bin/activate
```

## Structure du projet

```
dataset/          Échantillons audio d'enrôlement (.wav), un dossier par conducteur
test_samples/     Échantillons de test (jamais utilisés pour l'enrôlement)
models/           Modèles / embeddings sauvegardés (générés automatiquement)
pretrained_ecapa/ Cache du modèle ECAPA-TDNN pré-entraîné (généré au 1er lancement)
src/              Scripts Python
```

## Utilisation

Depuis `src/`, avec l'environnement virtuel activé :

**Enrôler un ou plusieurs conducteurs** (à faire une seule fois, ou quand on
ajoute un nouveau conducteur) :
```bash
python3 enrollment.py                     # enrôle tout dataset/
python3 enrollment.py --driver driver_3   # enrôle seulement un conducteur
```

**Identifier un échantillon** :
```bash
python3 identification.py ../test_samples/mon_fichier.wav
```

**Identification en continu depuis le micro** (nécessite un micro physique,
ne fonctionne pas dans un environnement type Codespace/CI) :
```bash
python3 streaming_identification.py
```

**Baseline GMM+MFCC** (comparaison avec l'approche classique) :
```bash
python3 gmm_mfcc_identification.py
```

## Approches implémentées

| Script | Méthode | Usage |
|---|---|---|
| `gmm_mfcc_identification.py` | MFCC + GMM | Baseline classique |
| `enrollment.py` / `identification.py` | ECAPA-TDNN (SpeechBrain) | Approche principale |
| `streaming_identification.py` | ECAPA-TDNN + VAD | Identification en flux continu |

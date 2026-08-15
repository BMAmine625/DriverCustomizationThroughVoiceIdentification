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

## Fonctionnalités

- **Deux approches d'identification** : baseline classique (MFCC + GMM) et
  approche principale par deep learning (ECAPA-TDNN pré-entraîné, via
  SpeechBrain), pour comparaison empirique dans le rapport.
- **Enrôlement et identification séparés** : l'enrôlement (calcul du profil
  d'un conducteur) ne se relance pas à chaque identification — les deux
  scripts sont indépendants.
- **Enrôlement automatique au micro** : plus besoin de créer des fichiers
  `.wav` à la main — un script enregistre directement les échantillons
  vocaux et enrôle le conducteur en une seule commande.
- **Identification en flux continu** : écoute le micro en direct, détecte
  automatiquement les segments de parole (VAD par énergie), et identifie
  le conducteur au fur et à mesure, sans intervention manuelle.
- **Adaptation incrémentale** : quand une identification est très
  confiante, le profil du conducteur reconnu est légèrement ajusté vers ce
  nouvel échantillon (moyenne mobile), pour s'adapter progressivement aux
  variations de voix/conditions, sans ré-entraînement complet.
- **Outils de diagnostic audio** : vérification rapide de la qualité des
  échantillons (durée, volume, taux de silence, saturation) pour repérer
  les enregistrements problématiques avant qu'ils ne faussent les résultats.
- **Installation reproductible en une commande** (`setup.sh`), avec gestion
  correcte des versions CPU de `torch`/`torchaudio` pour éviter les
  conflits CUDA.

## Structure du code (`src/`)

| Fichier | Rôle |
|---|---|
| `common.py` | Fonctions partagées : chargement du modèle ECAPA-TDNN, extraction d'embedding, similarité cosinus |
| `audio_capture.py` | Capture micro continue + détection de parole (VAD par énergie), partagée par le streaming et l'enrôlement automatique |
| `enrollment.py` | Enrôlement à partir de fichiers `.wav` déjà présents dans `dataset/` |
| `auto_enroll.py` | Enrôlement automatique : enregistre les échantillons au micro puis enrôle, en une seule commande |
| `identification.py` | Identification à partir d'un fichier `.wav`, avec adaptation incrémentale optionnelle |
| `streaming_identification.py` | Identification en direct sur flux micro continu |
| `record_test_sample.py` | Enregistre un échantillon de test unique avec la même qualité que l'enrôlement (évite les biais liés à des méthodes d'enregistrement différentes) |
| `diagnose_samples.py` | Diagnostic de qualité audio (durée, volume, silence, saturation) sur un dossier d'échantillons |
| `gmm_mfcc_identification.py` | Baseline classique MFCC + GMM (indépendante du reste, tout-en-un) |

## Utilisation

Depuis `src/`, avec l'environnement virtuel activé :

### Enrôlement

**Automatique, via le micro** (recommandé — garantit une qualité audio
cohérente, sans manipulation manuelle) :
```bash
python3 auto_enroll.py driver_1                  # enregistre 5 échantillons (défaut) et enrôle
python3 auto_enroll.py driver_1 --samples 8       # personnalise le nombre d'échantillons
```

**À partir de fichiers `.wav` déjà présents** dans `dataset/<nom>/` :
```bash
python3 enrollment.py                     # enrôle tous les conducteurs de dataset/
python3 enrollment.py --driver driver_3   # enrôle (ou ré-enrôle) un seul conducteur
```

### Identification

**Sur un fichier** :
```bash
python3 identification.py ../test_samples/mon_fichier.wav
python3 identification.py ../test_samples/mon_fichier.wav --no-adapt   # sans adaptation incrémentale
```

**En flux continu depuis le micro** (nécessite un micro physique, ne
fonctionne pas dans un environnement type Codespace/CI) :
```bash
python3 streaming_identification.py
```
Tape `q` puis Entrée pour arrêter proprement (`Ctrl+C` fonctionne aussi en secours).

### Outils annexes

**Enregistrer un échantillon de test propre** (même pipeline que l'enrôlement,
pour une comparaison cohérente) :
```bash
python3 record_test_sample.py test_1
```

**Vérifier la qualité d'un jeu d'échantillons** avant de s'en servir :
```bash
python3 diagnose_samples.py ../dataset/driver_1
python3 diagnose_samples.py ../test_samples
```

**Baseline GMM+MFCC** (comparaison avec l'approche principale) :
```bash
python3 gmm_mfcc_identification.py
```

## Approches implémentées

| Script | Méthode | Usage |
|---|---|---|
| `gmm_mfcc_identification.py` | MFCC + GMM | Baseline classique |
| `enrollment.py` / `auto_enroll.py` / `identification.py` | ECAPA-TDNN (SpeechBrain) | Approche principale |
| `streaming_identification.py` | ECAPA-TDNN + VAD | Identification en flux continu |

## Notes techniques

- L'identification rejette un locuteur comme `INCONNU` si le meilleur score
  de similarité cosinus est sous `ACCEPT_THRESHOLD` (0.25 par défaut,
  `identification.py`).
- L'adaptation incrémentale ne se déclenche que sur des scores très
  confiants (`CONFIDENT_THRESHOLD`, 0.45 par défaut) — volontairement plus
  strict que le seuil d'acceptation, pour éviter de dériver un profil sur
  une identification douteuse.
- La qualité des enregistrements (durée, volume, taux de silence) a un
  impact direct sur la fiabilité de l'identification — toujours utiliser
  `auto_enroll.py` / `record_test_sample.py` plutôt qu'un enregistrement
  manuel (ex. Audacity avec un mauvais gain), pour garantir des embeddings
  exploitables.

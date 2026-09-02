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
autres dépendances (dont l'API — voir plus bas).

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
preferences.json  Préférences par conducteur (siège, volant, rétroviseurs, climatisation)
src/              Scripts Python
driver_ui_project/driver_ui/  Client UI Kotlin/Jetpack Compose (voir section dédiée)
```

## Client UI (Kotlin / Jetpack Compose)

Un client graphique, indépendant des scripts Python (`driver_ui_project/driver_ui/`),
affiche visuellement les préférences d'un conducteur : un siège vu de profil
et les trois rétroviseurs, animés selon les valeurs reçues.

**État actuel :** interface fonctionnelle avec des données de test
(`MockPreferencesRepository.kt`), pas encore connectée à `api_server.py` —
le branchement réseau (REST/WebSocket) est la prochaine étape.

**Éléments visuels :**
- **Siège** : vue de profil avec repère tableau de bord/volant (pour situer
  l'avant du véhicule), coussin, dossier et appuie-tête solidaires. La
  position avant/arrière et la hauteur sont animées directement depuis les
  pourcentages du schéma de préférences. L'inclinaison du dossier est
  remappée depuis la plage brute (90–160°) vers une plage visuelle plus
  réaliste et plafonnée (0–40°), pour éviter un rendu physiquement
  impossible (dossier qui semble se replier sur l'assise).
- **Rétroviseurs** (gauche, intérieur, droit) : un repère (point) se déplace
  à l'intérieur de chaque glace selon les angles horizontal/vertical, pour
  une lecture claire du réglage même sur de petits angles.
- **Thème** : palette sombre inspirée des tableaux de bord automobiles
  modernes (bleu électrique, accent ambre), plutôt que le thème clair par
  défaut de Material Design.

**Structure du code (`app/src/main/java/com/pfa/driverui/`) :**

| Fichier | Rôle |
|---|---|
| `model/PreferencesModels.kt` | Classes de données reflétant le schéma `preferences.json` (siège, rétroviseurs) |
| `data/MockPreferencesRepository.kt` | Données de test (à remplacer par de vrais appels réseau) |
| `ui/theme/Theme.kt` | Palette de couleurs sombre, style tableau de bord |
| `ui/SeatView.kt` | Rendu animé du siège |
| `ui/MirrorView.kt` | Rendu animé d'un rétroviseur |
| `ui/CarPreferencesScreen.kt` | Écran principal (sélecteur de conducteur de test + panneaux) |
| `MainActivity.kt` | Point d'entrée de l'application |

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
- **Préférences conducteur en JSON** : chaque conducteur enrôlé peut
  configurer ses préférences (siège, volant, rétroviseurs, climatisation),
  soit juste après l'enrôlement, soit plus tard — saisie interactive
  guidée par un schéma, avec validation des plages de valeurs.
- **API REST + WebSocket** : un service (`api_server.py`) expose
  l'identification et les préférences pour un client externe (ex.
  application Kotlin sur Android Automotive OS), sans modifier les
  scripts existants — voir section dédiée plus bas.
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
| `audio_capture.py` | Capture micro continue + détection de parole (VAD par énergie), partagée par le streaming, l'enrôlement automatique et l'API |
| `enrollment.py` | Enrôlement à partir de fichiers `.wav` déjà présents dans `dataset/` |
| `auto_enroll.py` | Enrôlement automatique : enregistre les échantillons au micro, enrôle, puis propose de configurer les préférences |
| `identification.py` | Identification à partir d'un fichier `.wav`, avec adaptation incrémentale et chargement des préférences |
| `streaming_identification.py` | Identification en direct sur flux micro continu (usage terminal) |
| `preferences.py` | Chargement/sauvegarde des préférences JSON, saisie interactive validée par schéma, validation non-interactive (API), application (simulée) |
| `configure_preferences.py` | Configure ou modifie les préférences d'un conducteur à tout moment (terminal) |
| `record_test_sample.py` | Enregistre un échantillon de test unique avec la même qualité que l'enrôlement (évite les biais liés à des méthodes d'enregistrement différentes) |
| `diagnose_samples.py` | Diagnostic de qualité audio (durée, volume, silence, saturation) sur un dossier d'échantillons |
| `gmm_mfcc_identification.py` | Baseline classique MFCC + GMM (indépendante du reste, tout-en-un) |
| `api_server.py` | Service FastAPI : expose identification et préférences via REST/WebSocket pour un client externe |
| `test_ws_client.py` | Client de test en ligne de commande pour les WebSockets de l'API (en attendant un vrai client Kotlin) |

## Utilisation

Depuis `src/`, avec l'environnement virtuel activé :

### Enrôlement

**Automatique, via le micro** (recommandé — garantit une qualité audio
cohérente, sans manipulation manuelle) :
```bash
python3 auto_enroll.py driver_1                  # enregistre 5 échantillons (défaut) et enrôle
python3 auto_enroll.py driver_1 --samples 8       # personnalise le nombre d'échantillons
```
Propose ensuite de configurer les préférences du conducteur tout de suite,
ou plus tard.

**À partir de fichiers `.wav` déjà présents** dans `dataset/<nom>/` :
```bash
python3 enrollment.py                     # enrôle tous les conducteurs de dataset/
python3 enrollment.py --driver driver_3   # enrôle (ou ré-enrôle) un seul conducteur
```

### Préférences

**Configurer ou modifier à tout moment** :
```bash
python3 configure_preferences.py driver_1
```
Saisie guidée catégorie par catégorie (siège, volant, rétroviseurs,
climatisation), avec validation des plages de valeurs.

### Identification (terminal)

**Sur un fichier** :
```bash
python3 identification.py ../test_samples/mon_fichier.wav
python3 identification.py ../test_samples/mon_fichier.wav --no-adapt   # sans adaptation incrémentale
```
Charge et affiche automatiquement les préférences du conducteur identifié.

**En flux continu depuis le micro** :
```bash
python3 streaming_identification.py
python3 streaming_identification.py --no-adapt   # utile pour calibrer les seuils sans risque
```
Tape `q` puis Entrée pour arrêter proprement (`Ctrl+C` fonctionne aussi en secours).

### API (service pour un client externe)

**Lancer le service :**
```bash
uvicorn api_server:app --host 0.0.0.0 --port 8000
```

**Documentation interactive** (générée automatiquement, pratique pour tester
sans écrire de code client) :
```
http://localhost:8000/docs
```

**Endpoints REST principaux :**

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/health` | Vérifie que le service et le modèle sont bien chargés |
| GET | `/drivers` | Liste les conducteurs enrôlés |
| GET | `/preferences/schema` | Schéma des préférences (catégories, unités, bornes) |
| GET | `/preferences/{driver_name}` | Préférences d'un conducteur (404 si inconnu) |
| PUT | `/preferences/{driver_name}` | Enregistre des préférences (validées, 422 si hors plage) |
| POST | `/identify` | Identifie un conducteur à partir d'un fichier audio envoyé |

**Endpoints WebSocket (flux continu, micro du serveur) :**

| Route | Rôle |
|---|---|
| `/ws/identify` | Pousse un résultat d'identification à chaque phrase détectée |
| `/ws/enroll` | Enrôle un conducteur au micro, avec progression en direct |

**Tester les WebSockets en attendant un vrai client** (Kotlin ou autre) :
```bash
python3 test_ws_client.py identify
python3 test_ws_client.py identify --adapt
python3 test_ws_client.py enroll driver_3 --samples 5
python3 test_ws_client.py identify --host 192.168.1.42   # API sur une autre machine
```

**Important — où tourne le microphone :** le micro utilisé par `/ws/identify`
et `/ws/enroll` est celui de la machine qui **exécute** `api_server.py`, pas
celui du client qui s'y connecte. En déploiement réel, ce service tournerait
sur un calculateur compagnon embarqué (ex. Raspberry Pi relié au micro de
l'habitacle), et un client (Kotlin/Android Automotive, ou autre) s'y
connecterait via le réseau du véhicule.

**Note sur le réseau en conditions réelles :** aucune donnée audio ne
transite sur le réseau (le micro est local au service) — seuls de petits
messages JSON circulent entre client et serveur, ce qui reste léger quel
que soit le support réseau utilisé (Ethernet automobile 100BASE-T1, Wi-Fi
embarqué, ou liaison USB). Pour ce PFA, les tests se font en local (même
machine, `localhost`) ou sur un réseau Wi-Fi classique.

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
| `streaming_identification.py` / `api_server.py` | ECAPA-TDNN + VAD | Identification en flux continu (terminal ou service) |

## Structure des préférences (`preferences.json`)

| Catégorie | Champ | Unité | Plage |
|---|---|---|---|
| Siège | position avant/arrière | % | 0–100 |
| | hauteur | % | 0–100 |
| | inclinaison dossier | degrés | 90–160 |
| Volant | hauteur | % | 0–100 |
| | profondeur | % | 0–100 |
| Rétroviseurs (x2) | horizontal | degrés | -30 à 30 |
| | vertical | degrés | -20 à 20 |
| Climatisation | température | °C | 16–30 |
| | vitesse ventilation | palier | 1–7 |

Le schéma (`_schema` dans `preferences.json`) définit ces catégories/bornes
et peut être modifié librement — le code de saisie/validation (terminal et
API) s'adapte automatiquement à tout changement de structure.

## Notes techniques

- L'identification rejette un locuteur comme `INCONNU` si le meilleur score
  de similarité cosinus est sous `ACCEPT_THRESHOLD` (0.60, `identification.py`).
- L'adaptation incrémentale ne se déclenche que sur des scores très
  confiants (`CONFIDENT_THRESHOLD`, 0.75) — volontairement plus strict que
  le seuil d'acceptation, pour éviter de dériver un profil sur une
  identification douteuse.
- La qualité des enregistrements (durée, volume, taux de silence) a un
  impact direct sur la fiabilité de l'identification — toujours utiliser
  `auto_enroll.py` / `record_test_sample.py` plutôt qu'un enregistrement
  manuel (ex. Audacity avec un mauvais gain), pour garantir des embeddings
  exploitables.
- L'identification est légèrement moins fiable dans une langue différente
  de celle utilisée à l'enrôlement (dégradation cross-linguale, documentée
  dans la littérature et confirmée empiriquement sur ce projet) — enrôler
  un conducteur avec plusieurs langues qu'il utilise réellement rapproche
  les scores entre langues et rend le système plus robuste.
- L'API valide les préférences reçues (`PUT /preferences/{driver}`) avec
  les mêmes bornes que la saisie interactive en terminal — un client ne
  peut donc pas enregistrer une valeur hors plage, quelle que soit son
  origine (bug, saisie utilisateur invalide, etc.).

## Limitations connues

- **Distinction conducteur / passager** : avec un micro mono unique (pas
  de réseau de microphones), le système identifie *qui parle* parmi les
  conducteurs enrôlés, mais ne peut pas déterminer *où* cette personne est
  assise. Si un conducteur et un passager tous deux enrôlés parlent (à
  tour de rôle ou simultanément), le système ne peut pas garantir que
  seules les préférences du conducteur réel sont chargées. Résoudre ce
  problème nécessiterait un réseau de microphones avec estimation de la
  direction d'arrivée du son (direction of arrival / beamforming) —
  approche utilisée par certains fournisseurs du secteur (ex. Kardome) —
  ce qui est hors du scope matériel actuel de ce projet. Piste retenue
  comme travaux futurs, au même titre que l'extension à la reconnaissance
  faciale.
- **Parole simultanée** : le pipeline ne gère pas la séparation de
  locuteurs (speaker diarization/separation) — si deux personnes parlent
  en même temps, l'embedding calculé sur le mélange peut être dégradé ou
  ambigu.
- **Application matérielle des préférences** : `apply_preferences()` ne
  fait actuellement qu'un affichage simulé — aucun actionneur réel
  (siège, rétroviseurs, etc.) n'est branché. Le point d'intégration est
  clairement identifié dans le code (commentaire `TODO actionneur`).
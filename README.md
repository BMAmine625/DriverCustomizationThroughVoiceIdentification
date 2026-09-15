# Système de reconnaissance vocale pour personnalisation des préférences conducteur

Identification du conducteur par sa voix, pour charger automatiquement ses
préférences véhicule (siège, volant, rétroviseurs, climatisation), avec un
service backend Python et un client graphique Kotlin/Jetpack Compose.

## Installation (backend Python)

Une seule commande, depuis la racine du projet :

```bash
./setup.sh
```

Ce script crée un environnement virtuel Python, installe `torch`/`torchaudio`
en version CPU (nécessaire pour éviter des conflits CUDA), puis toutes les
autres dépendances (dont l'API).

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
src/              Scripts Python (identification, enrôlement, API)
driver_ui_project/driver_ui/  Client graphique Kotlin/Jetpack Compose
```

## Fonctionnalités (backend)

- **Deux approches d'identification** : baseline classique (MFCC + GMM) et
  approche principale par deep learning (ECAPA-TDNN pré-entraîné, via
  SpeechBrain), pour comparaison empirique dans le rapport.
- **Enrôlement et identification séparés**, avec adaptation incrémentale des
  profils sur identification très confiante.
- **API REST + WebSocket** (`api_server.py`) exposant l'identification, la
  gestion complète des profils (créer, lire, modifier, **renommer**,
  **supprimer**), et les préférences, pour un client externe.
- **Préférences en JSON**, schéma extensible (`_schema`), validées côté
  serveur avec bornes explicites.
- **Outils de diagnostic audio** pour repérer les enregistrements
  problématiques avant qu'ils ne faussent les résultats.

## Structure du code (`src/`)

| Fichier | Rôle |
|---|---|
| `common.py` | Chargement du modèle ECAPA-TDNN, extraction d'embedding, similarité cosinus |
| `audio_capture.py` | Capture micro continue + VAD, partagée par le streaming, l'enrôlement et l'API |
| `enrollment.py` | Enrôlement à partir de fichiers `.wav` déjà présents |
| `auto_enroll.py` | Enrôlement automatique au micro (terminal) |
| `identification.py` | Identification sur fichier, adaptation incrémentale |
| `streaming_identification.py` | Identification en direct (terminal) |
| `preferences.py` | Chargement/sauvegarde/validation des préférences JSON, suppression et renommage d'un profil |
| `configure_preferences.py` | Configure les préférences d'un conducteur (terminal) |
| `record_test_sample.py` | Enregistre un échantillon de test propre |
| `diagnose_samples.py` | Diagnostic de qualité audio |
| `gmm_mfcc_identification.py` | Baseline classique MFCC + GMM |
| `api_server.py` | Service FastAPI : identification et gestion complète des profils via REST/WebSocket |
| `test_ws_client.py` | Client de test en ligne de commande pour les WebSockets |

## API — endpoints

**REST :**

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/health` | Vérifie que le service et le modèle sont chargés |
| GET | `/drivers` | Liste les conducteurs enrôlés |
| DELETE | `/drivers/{name}` | Supprime un conducteur (profil vocal, préférences, échantillons audio) |
| POST | `/drivers/{old_name}/rename?new_name=...` | Renomme un conducteur (profil vocal, préférences, dossier d'échantillons) |
| GET | `/preferences/schema` | Schéma des préférences (catégories, unités, bornes) |
| GET | `/preferences/{name}` | Préférences d'un conducteur (404 si inconnu) |
| PUT | `/preferences/{name}` | Enregistre des préférences (validées, 422 si hors plage) |
| POST | `/identify` | Identifie un conducteur à partir d'un fichier audio envoyé |

**WebSocket (flux continu, micro du serveur) :**

| Route | Rôle |
|---|---|
| `/ws/identify` | Pousse un résultat d'identification à chaque phrase détectée |
| `/ws/enroll` | Enrôle un conducteur au micro, avec progression en direct |

**Lancer le service :**
```bash
uvicorn api_server:app --host 0.0.0.0 --port 8000
```
Documentation interactive : `http://<host>:8000/docs`.

**Important :** le microphone utilisé par `/ws/identify` et `/ws/enroll` est
celui de la machine qui exécute `api_server.py`, pas celui du client
connecté. Aucune donnée audio ne transite sur le réseau (seuls de petits
messages JSON circulent).

## Client graphique (Kotlin / Jetpack Compose)

Application autonome (`driver_ui_project/driver_ui/`) qui se connecte à
`api_server.py` en REST/WebSocket. Statut : flux complet testé et validé
(identification, création de profil avec enrôlement vocal intégré,
modification, renommage, suppression).

### Écrans

- **Listening** : écran d'accueil, invite à parler, connecté en continu à
  `/ws/identify` ; affiche un message si le conducteur n'est pas reconnu.
- **Identified** : transition brève affichant le nom du conducteur reconnu.
- **Preferences** (`CarPreferencesScreen`) : affichage animé des 4
  catégories (siège, volant, rétroviseurs x3, climatisation) selon les
  préférences reçues ; bouton pour revenir à l'écoute.
- **Settings** (icône engrenage, accessible depuis n'importe quel écran) :
  - configuration de l'adresse du serveur (persistée)
  - liste des profils enrôlés
  - créer un profil : saisie du nom → enrôlement vocal via `/ws/enroll`
    (progression en direct) → formulaire de préférences pré-rempli
  - modifier un profil : formulaire avec aperçu visuel en direct
    (les vues siège/volant/rétroviseurs/climatisation s'animent pendant la
    saisie) et boutons -/+ en plus de la saisie au clavier
  - renommer un profil (déplace profil vocal + préférences + dossier
    d'échantillons côté serveur)
  - supprimer un profil, avec confirmation

### Structure du code (`app/src/main/java/com/pfa/driverui/`)

| Fichier | Rôle |
|---|---|
| `model/PreferencesModels.kt` | Classes de données reflétant le schéma serveur |
| `network/ApiClient.kt` | Client REST + WebSocket (OkHttp), parsing JSON, delete/rename |
| `network/ServerConfig.kt` | Adresse du serveur persistée (SharedPreferences) |
| `ui/theme/Theme.kt` | Palette sombre, style tableau de bord automobile |
| `ui/SeatView.kt` / `SteeringWheelView.kt` / `MirrorView.kt` / `ClimateView.kt` | Rendus animés par catégorie |
| `ui/ListeningScreen.kt` / `IdentifiedScreen.kt` / `CarPreferencesScreen.kt` | Écrans du flux principal |
| `ui/SettingsScreen.kt` | Gestion des profils (créer/modifier/renommer/supprimer) |
| `ui/AppScreen.kt` / `DriverApp.kt` | État de navigation et orchestration (connexion WebSocket, overlay du bouton réglages) |
| `data/MockPreferencesRepository.kt` | Données de test hors-ligne, non utilisées dans le flux principal |

### Environnement de test

Testé sur l'émulateur Android Automotive avec Google Play (image
"Automotive Distant Display with Google Play", Android 13 "Tiramisu",
x86_64) fourni par Android Studio.

Note importante : l'application est actuellement une app Compose classique
(interface libre), pas une app conforme à la Car App Library imposée par
Google pour la distribution officielle sur le Play Store automobile (qui
impose des templates prédéfinis pour limiter la distraction au volant). Ce
choix est délibéré pour ce PFA — voir limitations plus bas. Le fait qu'elle
tourne sur l'émulateur Automotive confirme la compatibilité technique, mais
une adaptation aux templates serait nécessaire pour une distribution
officielle.

## Structure des préférences (`preferences.json`)

| Catégorie | Champ | Unité | Plage |
|---|---|---|---|
| Siège | position avant/arrière | % | 0–100 |
| | hauteur | % | 0–100 |
| | inclinaison dossier | degrés | 90–160 |
| Volant | hauteur | % | 0–100 |
| | profondeur | % | 0–100 |
| Rétroviseurs (x3 : gauche, droit, intérieur) | horizontal | degrés | -30 à 30 |
| | vertical | degrés | -20 à 20 |
| Climatisation | température | °C | 16–30 |
| | vitesse ventilation | palier | 1–7 |

Le schéma (`_schema` dans `preferences.json`) définit ces catégories/bornes
et peut être modifié librement — le code de saisie/validation (terminal et
API) s'adapte automatiquement.

## Notes techniques

- Seuils d'identification : `ACCEPT_THRESHOLD` = 0.60, `CONFIDENT_THRESHOLD`
  (déclenche l'adaptation) = 0.75.
- La qualité des enregistrements (durée, volume, silence) a un impact
  direct sur la fiabilité — toujours privilégier l'enrôlement automatique
  au micro plutôt qu'un enregistrement manuel.
- Dégradation cross-linguale confirmée empiriquement : enrôler un
  conducteur avec plusieurs langues qu'il utilise réellement rapproche les
  scores entre langues.
- L'API valide les préférences reçues avec les mêmes bornes que la saisie
  interactive en terminal.
- Supprimer ou renommer un conducteur agit à la fois sur son profil vocal
  (`enrolled_speakers.pkl`), ses préférences (`preferences.json`), et son
  dossier d'échantillons audio (`dataset/<nom>/`), pour éviter toute
  incohérence entre ces trois sources.

## Limitations connues

- **Distinction conducteur / passager** : avec un micro mono unique,
  impossible de déterminer la position physique du locuteur identifié.
  Nécessiterait un réseau de microphones (direction of arrival). Piste
  retenue comme travaux futurs, au même titre que la reconnaissance
  faciale.
- **Parole simultanée** : pas de séparation de locuteurs.
- **Application matérielle des préférences** : `apply_preferences()` ne
  fait qu'un affichage simulé côté serveur — aucun actionneur réel n'est
  encore branché. Du matériel (actionneurs) doit être fourni par
  l'encadrant ; leur nombre et leur nature exacts restent à confirmer.
  Le point d'intégration est déjà identifié dans le code (`preferences.py`,
  commentaire `TODO actionneur`).
- **Conformité Android Automotive officielle** : l'app ne suit pas les
  templates de la Car App Library (choix délibéré pour ce PFA, voir plus
  haut) — une vraie distribution sur le Play Store automobile
  nécessiterait cette adaptation.
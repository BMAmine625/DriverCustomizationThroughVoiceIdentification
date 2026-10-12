# Reconnaissance vocale du conducteur — personnalisation automatique du poste de conduite

Projet de Fin d'Année (ENSAF, filière ISEIA). Le système identifie le
conducteur par sa voix et applique ses préférences (siège, dossier, volant,
rétroviseurs, climatisation). Il se compose d'un **serveur Python** (FastAPI,
Raspberry Pi 4B), d'un **Arduino** qui pilote les actionneurs du siège et d'une
**application Android Automotive** (Kotlin / Jetpack Compose) avec une scène 3D
du véhicule.

```
Micro USB ─► Raspberry Pi (VAD + ECAPA-TDNN + API REST/WebSocket) ─► Application Android Automotive
                    │ I2C
                    ▼
              Arduino Nano ─► moteur pas-à-pas (siège) + servomoteur (dossier)
```

## Fonctionnement

- **Identification** : détection de parole (VAD), embedding ECAPA-TDNN
  pré-entraîné (SpeechBrain, 192 valeurs), similarité cosinus avec les profils
  enrôlés. Seuil d'acceptation 0,60 ; en dessous, le locuteur est « inconnu » et
  aucune préférence n'est chargée.
- **Amélioration automatique du profil** (option, désactivée par défaut, réglée
  par conducteur) : si le score dépasse 0,75, le profil est mis à jour par
  moyenne mobile (α = 0,1).
- **Enrôlement** au micro du serveur, avec reprise possible : *Re-enroll* (refait
  le profil à partir des enregistrements conservés) et *Record again* (remplace
  les enregistrements, uniquement une fois tous les nouveaux échantillons pris).
- **Méthode de référence** MFCC + GMM, pour comparaison avec ECAPA-TDNN.

## Installation du serveur

```bash
./setup.sh              # venv, torch CPU, dépendances
source venv/bin/activate
cd src && uvicorn api_server:app --host 0.0.0.0 --port 8000
```

Documentation interactive : `http://<hôte>:8000/docs`. Le microphone utilisé est
celui de la machine qui exécute le serveur ; seuls de petits messages JSON
transitent sur le réseau.

### Déploiement sur Raspberry Pi 4B (Raspberry Pi OS 64 bits)

```bash
sudo apt install -y portaudio19-dev libasound2-dev python3-dev
./setup.sh && pip install smbus2
sudo raspi-config        # Interface Options -> I2C -> Enable, puis redémarrer
```

Un swap de 1 Go est recommandé (2 Go de RAM). Pour un démarrage automatique,
créer `/etc/systemd/system/voice-driver-api.service` :

```ini
[Unit]
Description=API de reconnaissance vocale conducteur
After=network-online.target sound.target
Wants=network-online.target

[Service]
User=<utilisateur>
WorkingDirectory=<projet>/src
ExecStart=<projet>/venv/bin/python3 <projet>/src/api_server.py
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload && sudo systemctl enable --now voice-driver-api.service
sudo journalctl -u voice-driver-api.service -f      # logs
```

## API

| Méthode | Route | Rôle |
|---|---|---|
| GET | `/health` | État du service et du modèle |
| GET | `/drivers` | Conducteurs enrôlés |
| DELETE | `/drivers/{name}` | Supprime profil vocal, préférences et échantillons |
| POST | `/drivers/{old}/rename?new_name=` | Renomme un conducteur |
| GET / PUT | `/drivers/{name}/options` | Options du conducteur (amélioration automatique) |
| POST | `/drivers/{name}/reenroll` | Refait le profil à partir des audios conservés |
| GET | `/preferences/schema` | Catégories, unités et bornes |
| GET / PUT | `/preferences/{name}` | Lit / enregistre les préférences (validées, 422 hors plage) |
| POST | `/identify` | Identifie à partir d'un fichier audio |
| WS | `/ws/identify` | Un résultat par phrase détectée (`levels=true` : niveaux sonores pour la forme d'onde ; `adapt` : force l'option) |
| WS | `/ws/enroll` | Enrôlement au micro, progression en direct (`replace=true` : *Record again*) |

Les préférences sont validées côté serveur avec les bornes du schéma
(`preferences.json`) : siège avant/arrière et hauteur 0–100 %, dossier 90–160°,
volant 0–100 %, rétroviseurs ±30° / ±20°, climatisation 16–30 °C et ventilation 1–7.

## Application Android Automotive

Dossier `kotlin-app/` (paquet `com.pfa.driverui`). Écrans : écoute (scène 3D
floutée, forme d'onde), identification, préférences (scène nette, bande de
climatisation), réglages (profils, création avec enrôlement vocal, édition en
touchant les pièces du véhicule, renommage, suppression, option d'amélioration
automatique, Re-enroll / Record again, adresse du serveur) et **Customize view**
(appui long sur l'engrenage) pour régler caméras et repères tactiles de la scène.

Testée sur l'émulateur *Automotive Distant Display with Google Play* (Android 13).
La scène est un modèle glTF 2.0 rendu avec Filament / SceneView.

**Plateformes.** Le client n'a été développé que pour les véhicules **Android**
(Android Automotive). Le serveur ne dépend d'aucun client (REST/WebSocket, JSON) :
un client **iOS (CarPlay)** ou pour toute autre plateforme peut être réalisé sans
modifier le serveur.

## Matériel : actionneurs

Le Pi envoie à l'Arduino Nano (esclave I2C, adresse `0x08`) deux octets
`[axe, pourcentage]` : `1` = siège avant/arrière, `2` = inclinaison du dossier.
L'Arduino convertit et pilote seul le mouvement.

| Axe | Actionneur | Broches Arduino |
|---|---|---|
| Siège | Moteur pas-à-pas 42BYGHW811 + TB6600 (1/4 de pas) | PUL− D2, DIR− D3, ENA− D6 (PUL+/DIR+/ENA+ au 5 V) |
| Dossier | Servomoteur, 90–160° | Signal D9, alimentation 5 V dédiée |
| Position (prévu) | Encodeur KY-040 sur la vis | CLK D4, DT D5 |

Liaison I2C : SDA (GPIO2) – SDA, SCL (GPIO3) – SCL, masse commune obligatoire.
Mécanique du siège : réduction 1/3, vis de 8 mm/tour, course 200 mm. Le moteur et
le TB6600 ont leur propre alimentation. Les constantes sont dans
`arduino_stepper_i2c_slave.ino`.

## Structure du dépôt

| Élément | Rôle |
|---|---|
| `src/api_server.py` | Service FastAPI |
| `src/common.py`, `identification.py`, `enrollment.py`, `audio_capture.py` | Modèle, identification/adaptation, enrôlement, capture + VAD |
| `src/gmm_mfcc_identification.py` | Méthode de référence MFCC + GMM |
| `src/preferences.py`, `actuator_seat.py`, `actuator_backrest.py` | Préférences et ponts I2C |
| `arduino_stepper_i2c_slave.ino` | Firmware de l'Arduino |
| `kotlin-app/` | Application Android Automotive |
| `dataset/`, `models/`, `preferences.json`, `driver_options.json` | Données générées ou saisies |

## Limitations

- **Un seul micro mono** : impossible de distinguer conducteur et passager ; un
  réseau de microphones serait nécessaire.
- **Parole simultanée** : pas de séparation de locuteurs.
- **Motorisation partielle** : seuls le siège (avant/arrière) et le dossier sont
  motorisés ; le montage mécanique du dossier et ses butées sont à finaliser,
  l'encodeur n'est pas encore monté (siège en boucle ouverte) et la chaîne
  complète voix → actionneurs n'a pas fait l'objet d'un essai de bout en bout.
  Les autres préférences sont enregistrées et affichées, sans actionneur.
- **Amélioration automatique** : elle repose sur le score ; une erreur
  d'identification peut dériver le profil. Elle est donc optionnelle.
- **Sensibilité à la langue et à la qualité audio** : enrôler avec les langues
  réellement utilisées ; privilégier l'enrôlement au micro du serveur.
- **Évaluation limitée** : petit panel, environnement calme, pas d'EER ni de
  courbe DET.
- **Sécurité** : pas de détection d'usurpation (enregistrement rejoué) et API en
  HTTP sans authentification, à réserver à un réseau local.
- **Application Android uniquement**, non conforme aux modèles de la Car App
  Library (distribution officielle impossible en l'état) ; la bande de
  climatisation ne commande pas la climatisation réelle (permission système).
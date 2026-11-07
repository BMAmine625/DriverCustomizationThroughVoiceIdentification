# Reconnaissance vocale du conducteur — personnalisation automatique du poste de conduite

Projet de Fin d'Année (ENSAF, filière ISEIA). Le système identifie le
conducteur par sa voix et applique ses préférences (siège, dossier, volant,
rétroviseurs, climatisation). Il se compose d'un **serveur Python** (FastAPI,
Raspberry Pi 4B), d'un **Arduino** qui pilote les actionneurs du siège, relié au
Pi par un **bus CAN**, et d'une **application Android Automotive** (Kotlin /
Jetpack Compose) avec une scène 3D du véhicule.

```
Micro USB ─► Raspberry Pi (VAD + ECAPA-TDNN + API REST/WebSocket) ─► Application Android Automotive
                    │ bus CAN (HAT Waveshare 2-CH CAN, 500 kbit/s)
                    ▼
              Arduino Nano + module MCP2515 ─► moteur pas-à-pas (siège) + servomoteur (dossier)
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
sudo apt install -y portaudio19-dev libasound2-dev python3-dev can-utils
./setup.sh && pip install python-can     # dans le venv du projet
```

Un swap de 1 Go est recommandé (2 Go de RAM).

### Bus CAN (Pi → Arduino)

Le Pi utilise le HAT **Waveshare 2-CH CAN HAT** (deux MCP2515, quartz 16 MHz,
canal `can0`). Ajouter à `/boot/firmware/config.txt`, puis redémarrer :

```
dtparam=spi=on
dtoverlay=mcp2515-can0,oscillator=16000000,interrupt=23
```

Monter l'interface (500 kbit/s, identique des deux côtés) et vérifier :

```bash
sudo ip link set can0 up type can bitrate 500000
candump can0                      # dans un second terminal
cd src && python3 test_can.py seat 50       # siège à 50 %
python3 test_can.py backrest 0              # dossier au minimum
```

Le bus est choisi par la variable d'environnement `ACTUATOR_BUS` (`can` ou
`i2c`, défaut `i2c`). Pour lancer le serveur sur le bus CAN :

```bash
ACTUATOR_BUS=can uvicorn api_server:app --host 0.0.0.0 --port 8000
```

Variables facultatives : `CAN_CHANNEL` (défaut `can0`), `CAN_ID` (défaut `0x100`,
identique à `CAN_ID_COMMAND` dans le sketch).

### Démarrage automatique (systemd)

Deux unités : la première monte `can0`, la seconde lance l'API une fois le bus
prêt.

`/etc/systemd/system/can0.service`

```ini
[Unit]
Description=Interface CAN can0 (500 kbit/s)
After=sys-subsystem-net-devices-can0.device
Requires=sys-subsystem-net-devices-can0.device

[Service]
Type=oneshot
RemainAfterExit=yes
ExecStart=/sbin/ip link set can0 up type can bitrate 500000
ExecStop=/sbin/ip link set can0 down

[Install]
WantedBy=multi-user.target
```

`/etc/systemd/system/voice-driver-api.service`

```ini
[Unit]
Description=API de reconnaissance vocale conducteur
After=network-online.target sound.target can0.service
Wants=network-online.target can0.service

[Service]
User=<utilisateur>
WorkingDirectory=<projet>/src
Environment=ACTUATOR_BUS=can
ExecStart=<projet>/venv/bin/python3 <projet>/src/api_server.py
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now can0.service voice-driver-api.service
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

Le Pi envoie sur le bus CAN une trame standard (11 bits) d'identifiant `0x100`
contenant deux octets `[axe, pourcentage]` : `1` = siège avant/arrière,
`2` = inclinaison du dossier. L'Arduino convertit et pilote seul le mouvement.
La liaison CAN a été testée sur le montage et fonctionne.

Schéma de câblage complet : `docs/schema_cablage_CAN.pdf` (généré par
`docs/make_schematic_cablage_CAN.py`).

| Axe | Actionneur | Broches Arduino |
|---|---|---|
| Siège | Moteur pas-à-pas 42BYGHW811 + TB6600 (1/4 de pas) | PUL− D2, DIR− D3, ENA− D6 (PUL+/DIR+/ENA+ au 5 V) |
| Dossier | Servomoteur, 90–160° | Signal D9, alimentation 5 V dédiée |
| Position (prévu) | Encodeur KY-040 sur la vis | CLK D4, DT D5 |

**Module CAN côté Arduino** (MCP2515 + TJA1050, quartz 8 MHz) :

| Module | Arduino Nano |
|---|---|
| VCC / GND | 5 V / GND |
| CS | D10 |
| SI (MOSI) / SO (MISO) / SCK | D11 / D12 / D13 |
| INT | D7 |

**HAT côté Pi** : posé sur le connecteur GPIO ; cavalier de tension (VIO) sur
**3,3 V**. Liaison CANH ↔ CANH, CANL ↔ CANL, masse commune (borne G du HAT ↔ GND du
module). Résistance de terminaison de 120 Ω aux **deux extrémités** du bus
seulement (cavalier du HAT et cavalier J3 du module).

**Alimentation.** Le Pi a sa propre alimentation USB-C. Une alimentation 5 V
externe alimente l'Arduino, le module CAN, le servomoteur, l'encodeur et la
logique du TB6600 ; le moteur pas-à-pas et le TB6600 ont leur propre alimentation.
Ne pas brancher l'USB du Nano en même temps que le 5 V externe. Le servomoteur
demande des pointes de courant (alimentation ≥ 2 A) : placer un condensateur
(environ 470 µF) près de lui.

Mécanique du siège : réduction 1/3, vis de 8 mm/tour, course 200 mm. Les
constantes sont dans `arduino_stepper_can_slave.ino`. Bibliothèque Arduino
requise : **MCP2515** (autowp).

La version I²C (`arduino_stepper_i2c_slave.ino`, `test_stepper_i2c.py`) est
conservée ; elle s'utilise avec `ACTUATOR_BUS=i2c` (installer `smbus2`, activer
l'I²C avec `raspi-config`).

## Structure du dépôt

| Élément | Rôle |
|---|---|
| `src/api_server.py` | Service FastAPI |
| `src/common.py`, `identification.py`, `enrollment.py`, `audio_capture.py` | Modèle, identification/adaptation, enrôlement, capture + VAD |
| `src/gmm_mfcc_identification.py` | Méthode de référence MFCC + GMM |
| `src/preferences.py`, `actuator_seat.py`, `actuator_backrest.py` | Préférences et commandes des actionneurs |
| `src/actuator_link.py` | Transport Pi → Arduino (CAN ou I²C) |
| `src/test_can.py` | Test de la liaison CAN |
| `arduino_stepper_can_slave.ino` | Firmware de l'Arduino (CAN) |
| `arduino_stepper_i2c_slave.ino`, `test_stepper_i2c.py` | Version I²C conservée |
| `docs/` | Schéma de câblage CAN (PDF) et son générateur |
| `kotlin-app/` | Application Android Automotive |
| `dataset/`, `models/`, `preferences.json`, `driver_options.json` | Données générées ou saisies |

## Limitations

- **Un seul micro mono** : impossible de distinguer conducteur et passager ; un
  réseau de microphones serait nécessaire.
- **Parole simultanée** : pas de séparation de locuteurs.
- **Motorisation partielle** : seuls le siège (avant/arrière) et le dossier sont
  motorisés ; le montage mécanique du dossier et ses butées sont à finaliser et
  l'encodeur n'est pas encore monté (siège en boucle ouverte). Les autres
  préférences sont enregistrées et affichées, sans actionneur.
- **Liaison CAN à sens unique** : l'Arduino reçoit les commandes mais n'envoie ni
  accusé de réception ni position ; le Pi ne sait pas si le mouvement a abouti.
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
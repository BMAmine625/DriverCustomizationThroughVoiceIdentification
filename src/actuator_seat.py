"""
Contrôle du moteur pas-à-pas du rail de siège (avant/arrière)
==============================================================

Matériel (voir mémoire du projet / discussion séparée) :
    - Moteur : Wantai 42BYGHW811, 1.8°/pas (200 pas/tour en pas complet), 2.5A
    - Driver : carte microstep type TB6600 (entrées opto-isolées
      PUL+/PUL-, DIR+/DIR-, ENA+/ENA-, sorties bobinage A+/A-, B+/B-,
      DIP switches SW1-6 pour microstep/courant, alimentation moteur
      séparée DC 9-42V — jamais l'alimentation du Pi)

Câblage retenu (les + côté GPIO, les - reliés ensemble à la masse) :
    PUL+  -> GPIO_PUL (impulsion de pas)
    DIR+  -> GPIO_DIR (sens de rotation)
    ENA+  -> GPIO_ENA (activation du driver, actif HAUT — HIGH ou
                        déconnecté = activé, LOW = désactivé)
    PUL- / DIR- / ENA- -> reliés ensemble à une masse du Pi
    A+/A-, B+/B- -> bobinages du moteur Wantai

IMPORTANT : la masse du Pi doit aussi être reliée à la masse de
l'alimentation moteur (24V), en plus de cette masse de signal — sans
cette référence commune entre les deux alimentations séparées, le
driver interprète mal les impulsions et le moteur ne bouge pas
correctement (comportement erratique ou immobile).

Si le câblage réel est finalement inversé (PUL-/DIR-/ENA- côté Pi,
+/+/+ reliés à une tension fixe), inverser la logique HIGH/LOW dans
_pulse()/_enable()/_disable() ci-dessous en conséquence.

À CALIBRER avant tout mouvement réel — ne pas faire confiance aux
valeurs par défaut ci-dessous :
    - GPIO_PUL / GPIO_DIR / GPIO_ENA : les broches BCM réellement câblées
    - MICROSTEP : réglage des DIP switches SW1-3 du driver (1, 2, 4, 8,
      16 ... selon le modèle exact de la carte)
    - TOTAL_TRAVEL_STEPS : nombre de pas entre les deux butées
      mécaniques du rail (0 % et 100 %) — se mesure en comptant les
      pas nécessaires pour aller d'une butée à l'autre une fois le
      moteur monté
    - PULSE_DELAY : vitesse max sans décrochage (perte de pas) —
      commencer lent (valeur haute) et réduire progressivement en
      testant

Ce module tourne aussi bien sur le Pi (RPi.GPIO présent) que sur une
machine de dev sans GPIO : dans ce dernier cas, il passe en mode
simulation et affiche ce qu'il ferait, au lieu de planter à l'import.
"""

import json
import time
from pathlib import Path

try:
    import RPi.GPIO as GPIO
    _GPIO_AVAILABLE = True
except (ImportError, RuntimeError):
    _GPIO_AVAILABLE = False

# ---------------------------------------------------------------------
# Configuration matérielle — À ADAPTER à votre câblage réel
# ---------------------------------------------------------------------
GPIO_PUL = 20   # broche impulsion de pas (PUL+)
GPIO_DIR = 21   # broche sens (DIR+)
GPIO_ENA = 16   # broche activation (ENA+), actif haut (HIGH = activé)

STEPS_PER_REV = 200            # 1.8°/pas -> 200 pas/tour en pas complet
MICROSTEP = 1                   # à régler selon les DIP switches SW1-3
STEPS_PER_REV_EFFECTIVE = STEPS_PER_REV * MICROSTEP

TOTAL_TRAVEL_STEPS = 4000       # À CALIBRER : pas entre butée avant/arrière
PULSE_DELAY = 0.0015            # délai entre fronts (s) — à ajuster

STATE_FILE = Path(__file__).parent / "seat_actuator_state.json"


def _load_current_step() -> int:
    if STATE_FILE.exists():
        try:
            return json.loads(STATE_FILE.read_text())["current_step"]
        except (json.JSONDecodeError, KeyError, OSError):
            pass
    return 0


def _save_current_step(step: int) -> None:
    try:
        STATE_FILE.write_text(json.dumps({"current_step": step}))
    except OSError as e:
        print(f"[!] Impossible d'enregistrer la position du siège : {e}")


class SeatActuator:
    """
    Pilote le moteur pas-à-pas du rail de siège (avant/arrière).

    La position est suivie en pas absolus dans un petit fichier d'état
    (seat_actuator_state.json), car ce montage n'a ni fin de course ni
    encodeur pour se recaler automatiquement : sans ce fichier, une
    coupure de courant ferait perdre la position réelle connue.
    """

    def __init__(self):
        self._current_step = _load_current_step()
        if _GPIO_AVAILABLE:
            GPIO.setmode(GPIO.BCM)
            GPIO.setup(GPIO_PUL, GPIO.OUT, initial=GPIO.LOW)
            GPIO.setup(GPIO_DIR, GPIO.OUT, initial=GPIO.LOW)
            GPIO.setup(GPIO_ENA, GPIO.OUT, initial=GPIO.LOW)  # désactivé au repos
        else:
            print("[SeatActuator] RPi.GPIO indisponible — mode simulation.")

    def _enable(self):
        if _GPIO_AVAILABLE:
            GPIO.output(GPIO_ENA, GPIO.HIGH)  # actif haut -> driver activé
            time.sleep(0.01)

    def _disable(self):
        if _GPIO_AVAILABLE:
            GPIO.output(GPIO_ENA, GPIO.LOW)

    def _pulse(self, n_steps: int, direction: int):
        if not _GPIO_AVAILABLE:
            print(f"[SIMULATION] {n_steps} pas, direction={'+' if direction > 0 else '-'}")
            return
        GPIO.output(GPIO_DIR, GPIO.HIGH if direction > 0 else GPIO.LOW)
        time.sleep(0.005)  # laisse le driver lire DIR avant le 1er pas
        for _ in range(n_steps):
            GPIO.output(GPIO_PUL, GPIO.HIGH)
            time.sleep(PULSE_DELAY)
            GPIO.output(GPIO_PUL, GPIO.LOW)
            time.sleep(PULSE_DELAY)

    def move_to_percent(self, position_percent: float) -> None:
        """
        Déplace le siège pour atteindre `position_percent` (0-100).
        0 = butée arrière, 100 = butée avant — inverser le signe de
        `direction` ci-dessous si c'est l'inverse sur le montage réel.
        """
        position_percent = max(0.0, min(100.0, position_percent))
        target_step = round((position_percent / 100.0) * TOTAL_TRAVEL_STEPS)
        delta = target_step - self._current_step
        if delta == 0:
            return

        direction = 1 if delta > 0 else -1
        n_steps = abs(delta)

        self._enable()
        try:
            self._pulse(n_steps, direction)
            self._current_step = target_step
            _save_current_step(self._current_step)
        finally:
            self._disable()

    def home(self) -> None:
        """
        "Home" grossier : force le siège vers la butée arrière (au-delà
        du parcours connu, pour être sûr d'atteindre la butée physique),
        puis redéfinit cette position comme le pas 0.

        ATTENTION : ce driver TB6600 n'a ni détection de décrochage ni
        capteur de fin de course — le moteur pousse simplement contre
        la butée mécanique pendant l'excédent de pas. Vérifiez que le
        rail/la butée supporte ça avant d'appeler cette méthode. Pour
        une démo finale, un interrupteur de fin de course câblé sur un
        GPIO donnerait un homing propre plutôt que de forcer à l'aveugle.
        """
        self._enable()
        try:
            self._pulse(TOTAL_TRAVEL_STEPS + 200, direction=-1)
            self._current_step = 0
            _save_current_step(0)
        finally:
            self._disable()

    def cleanup(self) -> None:
        if _GPIO_AVAILABLE:
            GPIO.cleanup()


_actuator: "SeatActuator | None" = None


def get_seat_actuator() -> SeatActuator:
    """Instance unique (créée au premier appel) — évite de reconfigurer
    les GPIO à chaque application de préférences."""
    global _actuator
    if _actuator is None:
        _actuator = SeatActuator()
    return _actuator
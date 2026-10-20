"""
Actionneur dossier (inclinaison) - pont (I2C ou CAN) vers l'Arduino
====================================================================
Meme Arduino que actuator_seat.py : le sketch pilote 2 actionneurs en parallele,
le moteur pas-a-pas du siege (AXIS_SEAT) et un servo standard 0-180 deg pour le
dossier (AXIS_RECLINE). Le transport (I2C ou CAN) est choisi dans
actuator_link.py (variable ACTUATOR_BUS).

Contrairement au siege, "inclinaison_dossier" dans preferences.json est stocke
en DEGRES (schema : min 90, max 160). La commande transporte toujours un
pourcentage 0-100 sur 1 octet : c'est ce module qui fait la conversion
degres -> pourcentage avant l'envoi, avec ANGLE_MIN/ANGLE_MAX qui DOIVENT rester
identiques a RECLINE_MIN_ANGLE/RECLINE_MAX_ANGLE cote sketch Arduino (sinon
l'angle reellement atteint ne correspondra plus a la valeur enregistree).

Interface publique, pour preferences.py :
    get_backrest_actuator().move_to_angle(degres)
"""

import threading

from actuator_link import send_command

AXIS_RECLINE = 2        # doit correspondre a AXIS_RECLINE dans le sketch Arduino

# Doivent rester synchronises avec RECLINE_MIN_ANGLE / RECLINE_MAX_ANGLE
# dans le sketch Arduino (et avec le schema "inclinaison_dossier" de
# preferences.json, actuellement min=90/max=160).
ANGLE_MIN = 90
ANGLE_MAX = 160


class BackrestActuator:
    """Pont (I2C ou CAN) vers l'Arduino pour le servo d'inclinaison du dossier."""

    def __init__(self):
        self._lock = threading.Lock()

    def move_to_angle(self, angle_degrees: float) -> None:
        """
        Envoie une inclinaison cible, en degres (comme stocke dans
        preferences.json), a l'Arduino. Convertie ici en pourcentage 0-100,
        puis reconvertie en angle cote Arduino via RECLINE_MIN_ANGLE /
        RECLINE_MAX_ANGLE - les deux plages doivent donc rester les memes.

        Leve une exception si le bus ou l'Arduino n'est pas joignable ; a
        capturer cote appelant (voir apply_preferences dans preferences.py).
        """
        angle = max(ANGLE_MIN, min(ANGLE_MAX, angle_degrees))
        percent = int(round((angle - ANGLE_MIN) * 100 / (ANGLE_MAX - ANGLE_MIN)))

        with self._lock:
            send_command(AXIS_RECLINE, percent)  # I2C ou CAN, voir actuator_link.py


_actuator: "BackrestActuator | None" = None
_actuator_lock = threading.Lock()


def get_backrest_actuator() -> BackrestActuator:
    """Instance unique (creee au premier appel), thread-safe."""
    global _actuator
    if _actuator is None:
        with _actuator_lock:
            if _actuator is None:
                _actuator = BackrestActuator()
    return _actuator
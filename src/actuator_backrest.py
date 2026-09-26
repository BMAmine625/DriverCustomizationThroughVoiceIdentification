"""
Actionneur dossier (inclinaison) - pont I2C vers l'Arduino
============================================================
Meme Arduino/meme bus I2C que actuator_seat.py (adresse 0x08) : le
sketch (arduino_stepper_i2c_slave.ino) pilote maintenant 2 actionneurs
en parallele - le moteur pas-a-pas du siege (AXIS_SEAT) et un servo
standard 0-180 deg pour le dossier (AXIS_RECLINE), chacun avance
independamment dans loop() cote Arduino.

Contrairement au siege, "inclinaison_dossier" dans preferences.json
est stocke en DEGRES (schema : min 90, max 160 - voir preferences.json),
pas en pourcentage. Le protocole I2C, lui, transporte toujours un
pourcentage 0-100 sur 1 octet (comme pour le siege) : c'est ce module
qui fait la conversion degres -> pourcentage avant l'envoi, avec
ANGLE_MIN/ANGLE_MAX qui DOIVENT rester identiques a RECLINE_MIN_ANGLE/
RECLINE_MAX_ANGLE cote sketch Arduino (sinon l'angle reellement atteint
ne correspondra plus a la valeur enregistree dans les preferences).

Interface publique, pour preferences.py :
    get_backrest_actuator().move_to_angle(degres)
"""

import threading

from smbus2 import SMBus

I2C_BUS = 1
I2C_ADDRESS = 0x08     # meme Arduino/adresse que actuator_seat.py
AXIS_RECLINE = 2        # doit correspondre a AXIS_RECLINE dans le sketch Arduino

# Doivent rester synchronises avec RECLINE_MIN_ANGLE / RECLINE_MAX_ANGLE
# dans arduino_stepper_i2c_slave.ino (et avec le schema "inclinaison_dossier"
# de preferences.json, actuellement min=90/max=160).
ANGLE_MIN = 90
ANGLE_MAX = 160


class BackrestActuator:
    """Pont I2C vers l'Arduino pour le servo d'inclinaison du dossier."""

    def __init__(self, bus_num: int = I2C_BUS, address: int = I2C_ADDRESS):
        self._bus_num = bus_num
        self._address = address
        self._lock = threading.Lock()

    def move_to_angle(self, angle_degrees: float) -> None:
        """
        Envoie une inclinaison cible, en degres (comme stocke dans
        preferences.json), a l'Arduino par I2C. Convertie ici en
        pourcentage 0-100 (protocole du bus), puis reconvertie en angle
        cote Arduino via RECLINE_MIN_ANGLE/RECLINE_MAX_ANGLE - les deux
        plages doivent donc rester les memes des deux cotes.

        Leve une exception si le bus I2C ou l'Arduino n'est pas
        joignable ; a capturer cote appelant (voir apply_preferences
        dans preferences.py), comme pour l'actionneur siege.
        """
        angle = max(ANGLE_MIN, min(ANGLE_MAX, angle_degrees))
        percent = int(round((angle - ANGLE_MIN) * 100 / (ANGLE_MAX - ANGLE_MIN)))

        with self._lock:
            with SMBus(self._bus_num) as bus:
                bus.write_byte_data(self._address, AXIS_RECLINE, percent)


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

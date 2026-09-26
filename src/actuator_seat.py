"""
Actionneur siège (avant/arrière) - pont I2C vers l'Arduino
===========================================================
Historique : ce module pilotait le TB6600 en direct depuis les GPIO du
Pi (voir l'ancienne version, en dessous dans l'historique git/versions
si besoin). Ca a ete abandonne : le 3.3V des GPIO du Pi ne suffisait
pas a desactiver proprement l'opto ENA du driver (il faut ~5V pour un
etat "off" fiable). A la place, un Arduino Nano (alimente en 5V) fait
tout le pilotage bas niveau du TB6600 (PUL/DIR/ENA), et le Pi lui
envoie juste un pourcentage de course (0-100) par I2C. Voir
arduino_stepper_i2c_slave.ino pour le detail cote Arduino (conversion
pourcentage -> tick d'encodeur -> pas moteur, gestion d'ENA incluse :
le moteur n'est energise que pendant un deplacement).

Protocole I2C (mis a jour pour partager le bus avec le servo du
dossier, voir actuator_backrest.py) : 2 octets [axis, percent], avec
axis = AXIS_SEAT pour ce module. Cote smbus2, bus.write_byte_data(addr,
axis, percent) envoie exactement ces 2 octets - le "registre" de
write_byte_data n'est ici qu'un premier octet ordinaire lu par
l'Arduino, pas un vrai registre.

Interface publique inchangee pour preferences.py, aucun changement
necessaire de ce cote :
    get_seat_actuator().move_to_percent(percent)
"""

import threading

from smbus2 import SMBus

I2C_BUS = 1
I2C_ADDRESS = 0x08  # doit correspondre a I2C_ADDRESS dans le sketch Arduino
AXIS_SEAT = 1        # doit correspondre a AXIS_SEAT dans le sketch Arduino


class SeatActuator:
    """
    Pont I2C vers l'Arduino qui pilote reellement le moteur. Le Pi ne
    fait plus aucun calcul de pas/geometrie ici — tout ca vit maintenant
    dans le sketch Arduino (arduino_stepper_i2c_slave.ino), qui est
    aussi celui qui suit la position courante. Ce module se contente de
    transmettre le pourcentage demande.
    """

    def __init__(self, bus_num: int = I2C_BUS, address: int = I2C_ADDRESS):
        self._bus_num = bus_num
        self._address = address
        self._lock = threading.Lock()

    def move_to_percent(self, position_percent: float) -> None:
        """
        Envoie une position cible (0-100 %) a l'Arduino par I2C.

        Leve une exception si le bus I2C ou l'Arduino n'est pas
        joignable (bus non active, Arduino deconnecte/pas alimente,
        etc.) — preferences.py capture deja cette exception (voir
        apply_preferences) et continue sans bloquer le chargement des
        autres preferences du conducteur.
        """
        percent = int(round(max(0.0, min(100.0, position_percent))))

        with self._lock:
            with SMBus(self._bus_num) as bus:
                bus.write_byte_data(self._address, AXIS_SEAT, percent)


_actuator: "SeatActuator | None" = None
_actuator_lock = threading.Lock()


def get_seat_actuator() -> SeatActuator:
    """Instance unique (creee au premier appel), thread-safe — plusieurs
    requetes/threads (REST + websocket) peuvent appeler ceci en meme
    temps cote api_server.py."""
    global _actuator
    if _actuator is None:
        with _actuator_lock:
            if _actuator is None:
                _actuator = SeatActuator()
    return _actuator
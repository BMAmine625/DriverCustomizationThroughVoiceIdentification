"""
Actionneur siège (avant/arrière) - pont (I2C ou CAN) vers l'Arduino
====================================================================
Le Pi envoie juste un pourcentage de course (0-100) a l'Arduino Nano, qui fait
tout le pilotage bas niveau du TB6600 (PUL/DIR/ENA) : conversion pourcentage ->
tick d'encodeur -> pas moteur, gestion d'ENA incluse (le moteur n'est energise
que pendant un deplacement). Le transport (I2C ou CAN) est choisi dans
actuator_link.py (variable ACTUATOR_BUS).

Commande : 2 octets [axis, percent], avec axis = AXIS_SEAT pour ce module.

Interface publique inchangee pour preferences.py :
    get_seat_actuator().move_to_percent(percent)
"""

import threading

from actuator_link import send_command

AXIS_SEAT = 1        # doit correspondre a AXIS_SEAT dans le sketch Arduino


class SeatActuator:
    """
    Pont (I2C ou CAN) vers l'Arduino qui pilote reellement le moteur. Le Pi ne
    fait aucun calcul de pas/geometrie ici : tout vit dans le sketch Arduino,
    qui suit aussi la position courante. Ce module se contente de transmettre
    le pourcentage demande.
    """

    def __init__(self):
        self._lock = threading.Lock()

    def move_to_percent(self, position_percent: float) -> None:
        """
        Envoie une position cible (0-100 %) a l'Arduino (bus choisi dans actuator_link.py).

        Leve une exception si le bus ou l'Arduino n'est pas joignable —
        preferences.py capture deja cette exception (voir apply_preferences)
        et continue sans bloquer le chargement des autres preferences.
        """
        percent = int(round(max(0.0, min(100.0, position_percent))))

        with self._lock:
            send_command(AXIS_SEAT, percent)  # I2C ou CAN, voir actuator_link.py


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
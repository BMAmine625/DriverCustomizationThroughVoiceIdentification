"""
Liaison Pi -> Arduino (couche de transport des actionneurs)
============================================================
Un seul point d'envoi pour les commandes d'actionneurs, afin de pouvoir
passer de l'I2C au bus CAN sans toucher a actuator_seat.py,
actuator_backrest.py ni preferences.py.

Une commande = 2 octets : [axe, pourcentage]
    axe        : 1 = siege avant/arriere, 2 = inclinaison du dossier
    pourcentage: 0-100
Le contenu est identique sur les deux bus ; seul le transport change.

Choix du bus (variable d'environnement, defaut : i2c) :
    ACTUATOR_BUS=i2c   -> SMBus, adresse 0x08 (comportement actuel)
    ACTUATOR_BUS=can   -> python-can via SocketCAN (can0), trame standard

Parametres CAN (variables d'environnement) :
    CAN_CHANNEL   (defaut "can0")
    CAN_ID        (defaut 0x100, identifiant 11 bits de la commande ; doit etre
                   identique a CAN_ID_COMMAND dans arduino_stepper_can_slave.ino)

Materiel : HAT Waveshare 2-CH CAN HAT (2 x MCP2515, quartz 16 MHz) cote Pi, module
MCP2515/TJA1050 (quartz 8 MHz) cote Arduino. Debit commun : 500 kbit/s.
Cote Pi, l'interface se monte hors de Python (voir le README, section "Bus CAN") :
    sudo ip link set can0 up type can bitrate 500000
Ce module ne configure pas l'interface, il envoie seulement les trames.

NON TESTE sur materiel pour le CAN (pas de materiel disponible). Le chemin
I2C est identique au code precedent.
"""

import os
import threading

BUS = os.environ.get("ACTUATOR_BUS", "i2c").strip().lower()

# --- I2C ---
I2C_BUS = 1
I2C_ADDRESS = 0x08  # doit correspondre a I2C_ADDRESS dans le sketch Arduino

# --- CAN ---
CAN_CHANNEL = os.environ.get("CAN_CHANNEL", "can0")
CAN_ID = int(os.environ.get("CAN_ID", "0x100"), 0)

_lock = threading.Lock()
_can_bus = None


def _send_i2c(axis: int, percent: int) -> None:
    from smbus2 import SMBus
    with SMBus(I2C_BUS) as bus:
        # envoie exactement [axis, percent] (le "registre" est un octet ordinaire)
        bus.write_byte_data(I2C_ADDRESS, axis, percent)


def _send_can(axis: int, percent: int) -> None:
    global _can_bus
    import can  # python-can, importe seulement si le bus CAN est choisi
    if _can_bus is None:
        _can_bus = can.interface.Bus(channel=CAN_CHANNEL, interface="socketcan")
    msg = can.Message(arbitration_id=CAN_ID, is_extended_id=False,
                      data=[axis, percent])
    _can_bus.send(msg, timeout=0.5)


def send_command(axis: int, percent: int) -> None:
    """Envoie [axe, pourcentage] sur le bus choisi. Leve une exception si le
    bus ou l'Arduino n'est pas joignable (capturee dans apply_preferences)."""
    percent = int(max(0, min(100, percent)))
    with _lock:
        if BUS == "can":
            _send_can(axis, percent)
        elif BUS == "i2c":
            _send_i2c(axis, percent)
        else:
            raise ValueError(f"ACTUATOR_BUS inconnu : {BUS!r} (i2c ou can)")

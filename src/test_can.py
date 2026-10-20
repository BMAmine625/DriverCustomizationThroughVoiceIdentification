"""
Test CAN Pi -> Arduino : envoie une commande [axe, pourcentage] sur le bus CAN.

Prealable sur le Pi (voir README, section "Bus CAN") :
    sudo apt install -y can-utils && pip install python-can
    interface can0 montee a 500000 bit/s

Usage :
    python3 test_can.py seat 50        # siege a 50 % de la course
    python3 test_can.py backrest 0     # dossier a 0 % (angle mini)
Verification cote Pi, dans un autre terminal :  candump can0

Le test passe par actuator_link.py avec ACTUATOR_BUS=can, c'est-a-dire le meme
chemin que le serveur.
"""
import os
import sys

os.environ["ACTUATOR_BUS"] = "can"

from actuator_link import send_command  # noqa: E402

AXES = {"seat": 1, "backrest": 2}

if __name__ == "__main__":
    if len(sys.argv) != 3 or sys.argv[1] not in AXES:
        print("Usage : python3 test_can.py <seat|backrest> <pourcentage 0-100>")
        sys.exit(1)
    send_command(AXES[sys.argv[1]], int(sys.argv[2]))
    print(f"Trame envoyee : id 0x100, data [{AXES[sys.argv[1]]}, {int(sys.argv[2])}]")

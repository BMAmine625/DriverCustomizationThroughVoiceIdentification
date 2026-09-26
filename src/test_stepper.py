"""
Test I2C Pi -> Arduino : envoie un pourcentage (0-100) de la course du
siege a l'esclave I2C, qui fait toute la conversion pourcentage -> tick
d'encodeur -> pas moteur de son cote.

Installation prealable sur le Pi :
    pip install smbus2

Cablage :
    Pi SDA (GPIO2, pin 3)  -> Arduino SDA
    Pi SCL (GPIO3, pin 5)  -> Arduino SCL
    Pi GND                 -> Arduino GND (masse commune obligatoire)

Usage :
    python3 test_stepper_i2c.py <pourcentage>
    ex: python3 test_stepper_i2c.py 0     # bout de course (arriere)
        python3 test_stepper_i2c.py 50    # milieu de course
        python3 test_stepper_i2c.py 100   # bout de course (avant)
"""

import sys

from smbus2 import SMBus

I2C_BUS = 1        # bus I2C standard sur Raspberry Pi (GPIO2/GPIO3)
I2C_ADDRESS = 0x08  # doit correspondre a l'adresse dans le sketch Arduino


def send_target_percent(percent: int) -> None:
    percent = max(0, min(100, percent))

    with SMBus(I2C_BUS) as bus:
        bus.write_byte(I2C_ADDRESS, percent)

    print(f"Pourcentage cible envoye : {percent}%")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("Usage : python3 test_stepper_i2c.py <pourcentage 0-100>")
        sys.exit(1)

    target = int(sys.argv[1])
    send_target_percent(target)
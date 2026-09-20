"""
Test direct du moteur pas-à-pas (sans passer par la reconnaissance vocale)
============================================================================
Version cablage actif-bas (common-anode) : les "+" (PUL+/DIR+/ENA+) sont
tous relies au 5V du Pi, et les GPIO pilotent les "-" (PUL-/DIR-/ENA-).
GPIO LOW = signal actif (courant traverse l'opto), GPIO HIGH = inactif.

Usage :
    python3 test_stepper.py <GPIO_PUL> <GPIO_DIR> <GPIO_ENA> [n_steps] [delay]

Mapping reel confirme sur ce driver (etiquettes PUL/DIR/ENA du board ne
correspondent pas a leur vraie fonction pour DIR/ENA) :
    GPIO20 (pin 38) -> terminal "PUL-"                 (PUL reel)
    GPIO16 (pin 36) -> terminal "ENA-"  (= DIR- reel)   (DIR reel)
    GPIO21 (pin 40) -> terminal "DIR-"  (= ENA- reel)   (ENA reel)

    PUL+, ENA+, DIR+ (les 3, relies ensemble) -> 5V du Pi (pin 2 ou 4)

Exemple :
    python3 test_stepper.py 20 16 21 50 0.05
"""

import sys
import time

import RPi.GPIO as GPIO

if len(sys.argv) < 4:
    print(__doc__)
    sys.exit(1)

GPIO_PUL = int(sys.argv[1])
GPIO_DIR = int(sys.argv[2])
GPIO_ENA = int(sys.argv[3])
N_STEPS = int(sys.argv[4]) if len(sys.argv) > 4 else 50
PULSE_DELAY = float(sys.argv[5]) if len(sys.argv) > 5 else 0.05

print(f"[actif-bas] PUL -> GPIO{GPIO_PUL}, DIR -> GPIO{GPIO_DIR}, ENA -> GPIO{GPIO_ENA}")
print(f"{N_STEPS} pas par direction, delai {PULSE_DELAY}s entre fronts.\n")

GPIO.setmode(GPIO.BCM)
GPIO.setwarnings(False)
# Idle = HIGH (pas de courant), actif = LOW (courant via le 5V commun)
GPIO.setup(GPIO_PUL, GPIO.OUT, initial=GPIO.HIGH)
GPIO.setup(GPIO_DIR, GPIO.OUT, initial=GPIO.HIGH)
GPIO.setup(GPIO_ENA, GPIO.OUT, initial=GPIO.LOW)  # desactive au repos (logique inversee pour test)


def pulse(n_steps, direction, label):
    print(f"--- {label} : {n_steps} pas, direction={'A (+)' if direction > 0 else 'B (-)'} ---")
    GPIO.output(GPIO_ENA, GPIO.HIGH)  # INVERSE pour test -> active le driver
    time.sleep(0.2)

    GPIO.output(GPIO_DIR, GPIO.LOW if direction > 0 else GPIO.HIGH)
    time.sleep(0.05)

    for i in range(n_steps):
        GPIO.output(GPIO_PUL, GPIO.LOW)   # front actif
        time.sleep(PULSE_DELAY)
        GPIO.output(GPIO_PUL, GPIO.HIGH)  # retour idle
        time.sleep(PULSE_DELAY)

    GPIO.output(GPIO_ENA, GPIO.LOW)  # INVERSE pour test -> desactive
    print("(fin)\n")


try:
    input("Entree pour lancer le mouvement A...")
    pulse(N_STEPS, +1, "MOUVEMENT A")

    input("Entree pour lancer le mouvement B...")
    pulse(N_STEPS, -1, "MOUVEMENT B")

    print("Test termine.")

except KeyboardInterrupt:
    print("\nInterrompu.")

finally:
    GPIO.cleanup()
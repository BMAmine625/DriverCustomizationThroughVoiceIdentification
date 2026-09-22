"""
Test du moteur pas-a-pas via RpiMotorLib (au lieu du code GPIO ecrit a la main)
================================================================================
Installation prealable sur le Pi :
    pip install rpimotorlib[rpilgpio]

But : RpiMotorLib gere le PUL (step) et le DIR via sa methode motor_go(),
testee/utilisee par plein de monde -> permet d'eliminer un bug de logique
dans NOTRE code de generation des pas, en gardant le cablage actif-haut
d'origine (celui qui marchait pour ENA et pour la direction B).

Cablage ACTIF-HAUT (celui d'avant le passage en actif-bas) :
    GPIO20 (pin 38) -> terminal "PUL+"                  (PUL reel)
    GPIO16 (pin 36) -> terminal "ENA+"  (= DIR reel)     (DIR reel)
    GPIO21 (pin 40) -> terminal "DIR+"  (= ENA reel)     (ENA reel)
    PUL-/ENA-(sur "ENA+")/DIR-(sur "DIR+") -> masse commune du Pi (pin 39)

ENA reste gere a la main (RpiMotorLib ne le gere pas) : actif-HAUT, comme
confirme fonctionnel precedemment.

Usage :
    python3 test_stepper_lib.py [n_steps] [stepdelay]
"""

import sys
import time

import RPi.GPIO as GPIO
from RpiMotorLib import RpiMotorLib

GPIO_PUL = 20   # reel PUL -> step_pin pour la lib
GPIO_DIR = 16   # reel DIR -> direction_pin pour la lib
GPIO_ENA = 21   # reel ENA -> gere a la main, actif-HAUT

N_STEPS = int(sys.argv[1]) if len(sys.argv) > 1 else 50
STEP_DELAY = float(sys.argv[2]) if len(sys.argv) > 2 else 0.02

GPIO.setmode(GPIO.BCM)
GPIO.setwarnings(False)
GPIO.setup(GPIO_ENA, GPIO.OUT, initial=GPIO.LOW)  # desactive au repos (actif-HAUT)

# mode_pins=(-1,-1,-1) : pas de broches microstep, on utilise les DIP
# switches physiques du TB6600 (deja regles sur "1" = pas complet)
motor = RpiMotorLib.A4988Nema(GPIO_DIR, GPIO_PUL, (-1, -1, -1), "A4988")


def run(clockwise, label):
    print(f"\n--- {label} : {N_STEPS} pas, clockwise={clockwise} ---")
    GPIO.output(GPIO_ENA, GPIO.HIGH)  # active le driver
    time.sleep(0.2)

    motor.motor_go(
        clockwise=clockwise,
        steptype="Full",
        steps=N_STEPS,
        stepdelay=STEP_DELAY,
        verbose=True,
        initdelay=0.05,
    )

    GPIO.output(GPIO_ENA, GPIO.LOW)  # desactive
    print("(fin)")


try:
    input("Entree pour lancer le mouvement AVANT (clockwise=True)...")
    run(True, "MOUVEMENT AVANT")

    input("\nEntree pour lancer le mouvement ARRIERE (clockwise=False)...")
    run(False, "MOUVEMENT ARRIERE")

    print("\nTest termine.")

except KeyboardInterrupt:
    print("\nInterrompu.")

finally:
    GPIO.cleanup()
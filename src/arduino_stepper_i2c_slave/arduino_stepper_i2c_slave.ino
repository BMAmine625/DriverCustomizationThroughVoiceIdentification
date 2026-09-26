/*
 * Esclave I2C pilotant DEUX actionneurs du siege depuis le meme Arduino :
 *   - AXIS_SEAT    (0x01) : moteur pas-a-pas + TB6600 (avant/arriere du siege)
 *   - AXIS_RECLINE (0x02) : servo standard 0-180 deg (inclinaison du dossier)
 *
 * ---- Protocole I2C (mis a jour) ----
 *   Avant : le Pi envoyait 1 seul octet = pourcentage, tout pour le siege.
 *   Maintenant qu'il y a 2 actionneurs sur le meme bus/adresse, le Pi envoie
 *   2 octets : [axis, percent]
 *     axis    = AXIS_SEAT (1) ou AXIS_RECLINE (2)
 *     percent = 0-100, pourcentage de course (siege) ou d'angle (dossier)
 *   Cote Python : bus.write_byte_data(I2C_ADDRESS, axis, percent) envoie
 *   exactement ces 2 octets (le "registre" de write_byte_data devient
 *   simplement le 1er octet lu ici, pas un vrai registre).
 *   Adresse I2C esclave (inchangee) : 0x08
 *
 * ---- AXIS_SEAT : moteur pas-a-pas via TB6600 (inchange) ----
 * Cablage TB6600 (logique actif-bas) :
 *   PUL+, DIR+, ENA+ -> 5V de l'Arduino (fixe)
 *   D2                -> PUL-
 *   D3                -> DIR-
 *   D6                -> ENA-
 *
 * Cablage encodeur KY-040 (PAS ENCORE MONTE PHYSIQUEMENT) :
 *   CLK -> D4 (polling)  DT -> D5  +  -> 5V  GND -> GND
 *
 * Geometrie mecanique (mesuree) : reduction poulie 20/60 = 1/3, vis 4 filets
 * x 2mm mesure = 8mm/tour reel, course 200mm, moteur 200 pas/tour complet,
 * encodeur 20 ticks/tour de vis. Voir STEPS_PER_TICK/MAX_TICKS plus bas.
 *
 * ---- AXIS_RECLINE : servo standard pour le dossier (NOUVEAU) ----
 * Cablage :
 *   Signal -> D9 (PWM)
 *   V+     -> alimentation 5V EXTERNE, PAS la broche 5V de l'Arduino
 *             (un servo standard peut appeler plusieurs centaines de mA au
 *             demarrage/blocage - risque de reset de l'Arduino si alimente
 *             depuis son propre 5V ou depuis le port USB)
 *   GND    -> masse commune avec l'Arduino (obligatoire meme si
 *             l'alimentation est separee)
 *
 * Le servo a une boucle de position interne (potentiometre), donc
 * servo.write(angle) suffit - pas besoin de step/pulse comme le moteur pas-
 * a-pas. On limite juste la plage utile a [RECLINE_MIN_ANGLE, RECLINE_MAX_ANGLE]
 * (a ajuster une fois le dossier monte, pour ne pas forcer en butee), et on
 * deplace en douceur (1 deg a la fois, un delai fixe) plutot que de sauter
 * d'un coup a l'angle cible - plus confortable et evite un pic de courant.
 */

#include <Wire.h>
#include <Servo.h>

// Decommenter cette ligne une fois l'encodeur reellement monte sur la vis.
// #define USE_ENCODER

const uint8_t I2C_ADDRESS = 0x08;

const uint8_t AXIS_SEAT = 1;
const uint8_t AXIS_RECLINE = 2;

// ---------------- AXIS_SEAT : moteur pas-a-pas ----------------
const int PUL_PIN = 2;
const int DIR_PIN = 3;
const int ENA_PIN = 6;

const unsigned long ENA_SETTLE_MS = 5;

#ifdef USE_ENCODER
const int ENCODER_CLK_PIN = 4;
const int ENCODER_DT_PIN = 5;
#endif

const long TRAVEL_MM = 200;
const long MOTOR_STEPS_PER_REV = 200;
const long GEAR_RATIO = 3;
const long LEAD_MM_PER_REV = 8;
const long ENCODER_TICKS_PER_REV = 20;

const long STEPS_PER_TICK = (MOTOR_STEPS_PER_REV * GEAR_RATIO) / ENCODER_TICKS_PER_REV; // = 30
const long MAX_TICKS = (TRAVEL_MM * ENCODER_TICKS_PER_REV) / LEAD_MM_PER_REV;           // = 500

const unsigned long STEP_INTERVAL_US = 2000;
const unsigned long PULSE_WIDTH_US = 20;

volatile long targetSteps = 0;
long currentSteps = 0;
bool motorEnabled = false;

#ifdef USE_ENCODER
volatile long encoderTicks = 0;
long lastEncoderClkState = HIGH;
#endif

unsigned long lastStepMicros = 0;

// ---------------- AXIS_RECLINE : servo dossier ----------------
const int SERVO_PIN = 9;

// A ajuster physiquement une fois le dossier monte : plage d'angle reelle
// utile du servo, pour ne jamais forcer contre une butee mecanique.
const int RECLINE_MIN_ANGLE = 90;
const int RECLINE_MAX_ANGLE = 160;

const unsigned long RECLINE_STEP_INTERVAL_MS = 15; // vitesse du mouvement doux (plus petit = plus rapide)

Servo reclineServo;
volatile int targetReclineAngle = RECLINE_MIN_ANGLE;
int currentReclineAngle = RECLINE_MIN_ANGLE;
unsigned long lastReclineStepMillis = 0;
bool reclineAttached = false;

void onI2cReceive(int numBytes) {
  if (numBytes < 2) {
    while (Wire.available()) Wire.read();
    return;
  }
  uint8_t axis = Wire.read();
  uint8_t percent = Wire.read();
  while (Wire.available()) Wire.read(); // ignore les octets en trop

  if (percent > 100) percent = 100;

  if (axis == AXIS_SEAT) {
    long targetTick = ((long)percent * MAX_TICKS + 50) / 100; // arrondi
    targetSteps = targetTick * STEPS_PER_TICK;
    Serial.print("siege : ");
    Serial.print(percent);
    Serial.print("% -> tick ");
    Serial.print(targetTick);
    Serial.print(" -> pas cible ");
    Serial.println(targetSteps);
  } else if (axis == AXIS_RECLINE) {
    targetReclineAngle = map(percent, 0, 100, RECLINE_MIN_ANGLE, RECLINE_MAX_ANGLE);
    Serial.print("dossier : ");
    Serial.print(percent);
    Serial.print("% -> angle cible ");
    Serial.println(targetReclineAngle);
  }
  // axis inconnu -> ignore silencieusement
}

void enableMotor() {
  if (motorEnabled) return;
  digitalWrite(ENA_PIN, LOW);
  delay(ENA_SETTLE_MS);
  motorEnabled = true;
}

void disableMotor() {
  if (!motorEnabled) return;
  digitalWrite(ENA_PIN, HIGH);
  motorEnabled = false;
}

#ifdef USE_ENCODER
void pollEncoder() {
  int clkState = digitalRead(ENCODER_CLK_PIN);
  if (clkState != lastEncoderClkState && clkState == LOW) {
    if (digitalRead(ENCODER_DT_PIN) != clkState) encoderTicks++;
    else encoderTicks--;
  }
  lastEncoderClkState = clkState;
}
#endif

void serviceStepper() {
  if (currentSteps == targetSteps) {
    disableMotor();
    return;
  }
  enableMotor();
  unsigned long now = micros();
  if (now - lastStepMicros < STEP_INTERVAL_US) return;
  lastStepMicros = now;
  bool forward = targetSteps > currentSteps;
  digitalWrite(DIR_PIN, forward ? LOW : HIGH);
  digitalWrite(PUL_PIN, LOW);
  delayMicroseconds(PULSE_WIDTH_US);
  digitalWrite(PUL_PIN, HIGH);
  currentSteps += forward ? 1 : -1;
}

void serviceRecline() {
  if (currentReclineAngle == targetReclineAngle) {
    // Rien a faire : on peut detacher le servo pour ne pas le faire
    // "trembler"/chauffer pour rien une fois en position (autobloquant par
    // engrenages internes, comme la vis du siege).
    if (reclineAttached) {
      reclineServo.detach();
      reclineAttached = false;
    }
    return;
  }
  if (!reclineAttached) {
    reclineServo.attach(SERVO_PIN);
    reclineAttached = true;
  }
  unsigned long now = millis();
  if (now - lastReclineStepMillis < RECLINE_STEP_INTERVAL_MS) return;
  lastReclineStepMillis = now;
  currentReclineAngle += (targetReclineAngle > currentReclineAngle) ? 1 : -1;
  reclineServo.write(currentReclineAngle);
}

void setup() {
  pinMode(PUL_PIN, OUTPUT); pinMode(DIR_PIN, OUTPUT); pinMode(ENA_PIN, OUTPUT);
  digitalWrite(PUL_PIN, HIGH); digitalWrite(DIR_PIN, HIGH); digitalWrite(ENA_PIN, HIGH);
#ifdef USE_ENCODER
  pinMode(ENCODER_CLK_PIN, INPUT_PULLUP); pinMode(ENCODER_DT_PIN, INPUT_PULLUP);
#endif

  // Le servo n'est attache (PWM actif) que pendant un mouvement, voir
  // serviceRecline() - au demarrage on le laisse detache.
  currentReclineAngle = RECLINE_MIN_ANGLE;
  targetReclineAngle = RECLINE_MIN_ANGLE;

  Wire.begin(I2C_ADDRESS);
  Wire.onReceive(onI2cReceive);
  Serial.begin(115200);
  Serial.print("Esclave I2C pret, adresse 0x08. STEPS_PER_TICK=");
  Serial.print(STEPS_PER_TICK); Serial.print(" MAX_TICKS="); Serial.println(MAX_TICKS);
}

void loop() {
#ifdef USE_ENCODER
  pollEncoder();
#endif
  serviceStepper();
  serviceRecline();
}

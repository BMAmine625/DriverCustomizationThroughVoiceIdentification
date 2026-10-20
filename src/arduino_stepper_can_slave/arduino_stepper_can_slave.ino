/*
 * Noeud CAN pilotant DEUX actionneurs du siege depuis le meme Arduino Nano :
 *   - AXIS_SEAT    (0x01) : moteur pas-a-pas + TB6600 (avant/arriere du siege)
 *   - AXIS_RECLINE (0x02) : servo standard 0-180 deg (inclinaison du dossier)
 *
 * Version CAN de arduino_stepper_i2c_slave.ino (la logique des actionneurs est
 * identique ; seul le transport change). NON TESTE sur materiel.
 *
 * ---- Protocole CAN ----
 *   Trame standard (11 bits), identifiant CAN_ID_COMMAND = 0x100, 2 octets :
 *     data[0] = axe    (AXIS_SEAT = 1 ou AXIS_RECLINE = 2)
 *     data[1] = pourcentage 0-100 (course du siege / angle du dossier)
 *   Meme contenu que la trame I2C [axis, percent]. Debit : 500 kbit/s.
 *   Les trames d'un autre identifiant ou plus courtes que 2 octets sont ignorees
 *   (filtre materiel du MCP2515 sur 0x100 + verification de dlc).
 *
 * ---- Module CAN (MCP2515 + TJA1050, quartz 8 MHz) -> Arduino Nano ----
 *   VCC -> 5V        GND -> GND
 *   CS  -> D10       SI (MOSI) -> D11    SO (MISO) -> D12    SCK -> D13
 *   INT -> D7  (niveau bas quand une trame est recue ; sert a ne lire le
 *               MCP2515 que s'il y a quelque chose, pour ne pas perturber la
 *               cadence des impulsions du moteur pas-a-pas)
 *   CANH / CANL -> bus CAN (vers le HAT du Pi). Cavalier J3 = resistance de
 *   terminaison 120 ohms : a laisser aux DEUX extremites du bus seulement.
 *
 * Bibliotheque : "mcp2515" de autowp (Gestionnaire de bibliotheques Arduino,
 * ou https://github.com/autowp/arduino-mcp2515). Le quartz du module est de
 * 8 MHz (inscrit sur le boitier) : MCP_8MHZ ci-dessous doit correspondre.
 *
 * ---- Broches deja utilisees (inchangees) ----
 *   D2 PUL-   D3 DIR-   D6 ENA-   D9 servo   (D4/D5 : encodeur KY-040, non monte)
 *   Le bus SPI occupe D10-D13 : aucun conflit.  A4/A5 (I2C) ne sont plus utilises.
 *
 * ---- AXIS_SEAT : moteur pas-a-pas via TB6600 ----
 *   PUL+, DIR+, ENA+ -> 5V (fixe) ; D2 -> PUL-, D3 -> DIR-, D6 -> ENA-
 *   ENA : polarite confirmee inversee sur ce montage (26/09), compensee par
 *   ENA_ACTIVE_LOW = false. Remettre true si le cablage ENA est corrige.
 *   Geometrie : reduction 1/3, vis 8 mm/tour, course 200 mm, 1/4 de pas
 *   (800 impulsions/tour moteur), encodeur 20 ticks/tour de vis (non monte :
 *   boucle ouverte tant que USE_ENCODER est commente).
 *
 * ---- AXIS_RECLINE : servo standard ----
 *   Signal -> D9 ; V+ -> alimentation 5V externe (pas la broche 5V de l'Arduino) ;
 *   GND commun. Plage utile RECLINE_MIN_ANGLE..RECLINE_MAX_ANGLE a ajuster une
 *   fois le dossier monte ; ces valeurs doivent rester celles de ANGLE_MIN /
 *   ANGLE_MAX dans actuator_backrest.py.
 */

#include <SPI.h>
#include <mcp2515.h>
#include <Servo.h>

// Decommenter cette ligne une fois l'encodeur reellement monte sur la vis.
// #define USE_ENCODER

// ---------------- CAN ----------------
const uint32_t CAN_ID_COMMAND = 0x100;   // doit correspondre a CAN_ID dans actuator_link.py
const uint8_t  CAN_CS_PIN  = 10;
const uint8_t  CAN_INT_PIN = 7;
MCP2515 mcp2515(CAN_CS_PIN);

const uint8_t AXIS_SEAT = 1;
const uint8_t AXIS_RECLINE = 2;

// ---------------- AXIS_SEAT : moteur pas-a-pas ----------------
const int PUL_PIN = 2;
const int DIR_PIN = 3;
const int ENA_PIN = 6;

const unsigned long ENA_SETTLE_MS = 5;
const bool ENA_ACTIVE_LOW = false;

#ifdef USE_ENCODER
const int ENCODER_CLK_PIN = 4;
const int ENCODER_DT_PIN = 5;
#endif

const long TRAVEL_MM = 200;
const long MOTOR_FULL_STEPS_PER_REV = 200;
const long MICROSTEP_DIVISOR = 4;
const long MOTOR_STEPS_PER_REV = MOTOR_FULL_STEPS_PER_REV * MICROSTEP_DIVISOR; // 800
const long GEAR_RATIO = 3;
const long LEAD_MM_PER_REV = 8;
const long ENCODER_TICKS_PER_REV = 20;

const long STEPS_PER_TICK = (MOTOR_STEPS_PER_REV * GEAR_RATIO) / ENCODER_TICKS_PER_REV; // 120
const long MAX_TICKS = (TRAVEL_MM * ENCODER_TICKS_PER_REV) / LEAD_MM_PER_REV;           // 500

const unsigned long STEP_INTERVAL_US = 2000 / MICROSTEP_DIVISOR; // 500
const unsigned long PULSE_WIDTH_US = 20;

long targetSteps = 0;
long currentSteps = 0;
bool motorEnabled = false;

#ifdef USE_ENCODER
long encoderTicks = 0;
long lastEncoderClkState = HIGH;
#endif

unsigned long lastStepMicros = 0;

// ---------------- AXIS_RECLINE : servo dossier ----------------
const int SERVO_PIN = 9;
const int RECLINE_MIN_ANGLE = 90;
const int RECLINE_MAX_ANGLE = 160;
const unsigned long RECLINE_STEP_INTERVAL_MS = 15;

Servo reclineServo;
int targetReclineAngle = RECLINE_MIN_ANGLE;
int currentReclineAngle = RECLINE_MIN_ANGLE;
unsigned long lastReclineStepMillis = 0;
bool reclineAttached = false;

// Traite une commande [axis, percent] (meme logique que l'ancien onI2cReceive)
void handleCommand(uint8_t axis, uint8_t percent) {
  if (percent > 100) percent = 100;

  if (axis == AXIS_SEAT) {
    long targetTick = ((long)percent * MAX_TICKS + 50) / 100; // arrondi
    targetSteps = targetTick * STEPS_PER_TICK;
    Serial.print(F("siege : ")); Serial.print(percent);
    Serial.print(F("% -> tick ")); Serial.print(targetTick);
    Serial.print(F(" -> pas cible ")); Serial.println(targetSteps);
  } else if (axis == AXIS_RECLINE) {
    targetReclineAngle = map(percent, 0, 100, RECLINE_MIN_ANGLE, RECLINE_MAX_ANGLE);
    Serial.print(F("dossier : ")); Serial.print(percent);
    Serial.print(F("% -> angle cible ")); Serial.println(targetReclineAngle);
  }
  // axe inconnu -> ignore silencieusement
}

// Lit une trame seulement si le MCP2515 signale (INT bas) qu'une est arrivee
void serviceCan() {
  if (digitalRead(CAN_INT_PIN) != LOW) return;
  struct can_frame frame;
  while (mcp2515.readMessage(&frame) == MCP2515::ERROR_OK) {
    if (frame.can_id == CAN_ID_COMMAND && frame.can_dlc >= 2) {
      handleCommand(frame.data[0], frame.data[1]);
    }
  }
}

void enableMotor() {
  if (motorEnabled) return;
  digitalWrite(ENA_PIN, ENA_ACTIVE_LOW ? LOW : HIGH);
  delay(ENA_SETTLE_MS);
  motorEnabled = true;
}

void disableMotor() {
  if (!motorEnabled) return;
  digitalWrite(ENA_PIN, ENA_ACTIVE_LOW ? HIGH : LOW);
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
  digitalWrite(PUL_PIN, HIGH); digitalWrite(DIR_PIN, HIGH);
  digitalWrite(ENA_PIN, ENA_ACTIVE_LOW ? HIGH : LOW); // repos = desenergise
#ifdef USE_ENCODER
  pinMode(ENCODER_CLK_PIN, INPUT_PULLUP); pinMode(ENCODER_DT_PIN, INPUT_PULLUP);
#endif
  pinMode(CAN_INT_PIN, INPUT_PULLUP);

  currentReclineAngle = RECLINE_MIN_ANGLE;
  targetReclineAngle = RECLINE_MIN_ANGLE;

  Serial.begin(115200);

  SPI.begin();
  mcp2515.reset();
  mcp2515.setBitrate(CAN_500KBPS, MCP_8MHZ);
  // Filtre materiel : on ne recoit que l'identifiant de commande (11 bits)
  mcp2515.setFilterMask(MCP2515::MASK0, false, 0x7FF);
  mcp2515.setFilter(MCP2515::RXF0, false, CAN_ID_COMMAND);
  mcp2515.setFilter(MCP2515::RXF1, false, CAN_ID_COMMAND);
  mcp2515.setFilterMask(MCP2515::MASK1, false, 0x7FF);
  mcp2515.setFilter(MCP2515::RXF2, false, CAN_ID_COMMAND);
  mcp2515.setFilter(MCP2515::RXF3, false, CAN_ID_COMMAND);
  mcp2515.setFilter(MCP2515::RXF4, false, CAN_ID_COMMAND);
  mcp2515.setFilter(MCP2515::RXF5, false, CAN_ID_COMMAND);
  mcp2515.setNormalMode();

  Serial.print(F("Noeud CAN pret, id 0x100, 500 kbit/s. STEPS_PER_TICK="));
  Serial.print(STEPS_PER_TICK); Serial.print(F(" MAX_TICKS=")); Serial.println(MAX_TICKS);
}

void loop() {
#ifdef USE_ENCODER
  pollEncoder();
#endif
  serviceCan();
  serviceStepper();
  serviceRecline();
}

package com.pfa.driverui.model

/**
 * Reflète exactement la structure de préférences définie côté serveur
 * (preferences.json / _schema) : siège, volant, 3 rétroviseurs,
 * climatisation.
 */

data class SeatPreferences(
    val positionAvantArriere: Float, // %, 0-100
    val hauteur: Float,              // %, 0-100
    val inclinaisonDossier: Float,   // degrés, 90-160
)

data class SteeringWheelPreferences(
    val hauteur: Float,     // %, 0-100
    val profondeur: Float,  // %, 0-100
)

data class MirrorPreferences(
    val horizontal: Float, // degrés, -30 à 30
    val vertical: Float,   // degrés, -20 à 20
)

data class MirrorsPreferences(
    val gauche: MirrorPreferences,
    val droit: MirrorPreferences,
    val interieur: MirrorPreferences,
)

data class ClimatePreferences(
    val temperatureCelsius: Float, // °C, 16-30
    val vitesseVentilation: Float, // palier, 1-7
)

data class DriverPreferences(
    val seat: SeatPreferences,
    val steeringWheel: SteeringWheelPreferences,
    val mirrors: MirrorsPreferences,
    val climate: ClimatePreferences,
)

/** Valeurs par défaut neutres, utilisées tant qu'aucun conducteur n'est identifié. */
val DEFAULT_PREFERENCES = DriverPreferences(
    seat = SeatPreferences(positionAvantArriere = 50f, hauteur = 50f, inclinaisonDossier = 110f),
    steeringWheel = SteeringWheelPreferences(hauteur = 50f, profondeur = 50f),
    mirrors = MirrorsPreferences(
        gauche = MirrorPreferences(horizontal = 0f, vertical = 0f),
        droit = MirrorPreferences(horizontal = 0f, vertical = 0f),
        interieur = MirrorPreferences(horizontal = 0f, vertical = 0f),
    ),
    climate = ClimatePreferences(temperatureCelsius = 21f, vitesseVentilation = 3f),
)

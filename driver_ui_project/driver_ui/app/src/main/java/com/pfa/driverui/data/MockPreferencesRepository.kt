package com.pfa.driverui.data

import com.pfa.driverui.model.ClimatePreferences
import com.pfa.driverui.model.DriverPreferences
import com.pfa.driverui.model.MirrorPreferences
import com.pfa.driverui.model.MirrorsPreferences
import com.pfa.driverui.model.SeatPreferences
import com.pfa.driverui.model.SteeringWheelPreferences

/**
 * Données de référence hors-ligne — plus utilisées dans le flux
 * principal de l'app (qui reçoit maintenant les vraies préférences via
 * l'API), gardées pour prévisualisation/tests ponctuels si besoin.
 */
object MockPreferencesRepository {

    val driver1 = DriverPreferences(
        seat = SeatPreferences(positionAvantArriere = 45f, hauteur = 30f, inclinaisonDossier = 100f),
        steeringWheel = SteeringWheelPreferences(hauteur = 40f, profondeur = 30f),
        mirrors = MirrorsPreferences(
            gauche = MirrorPreferences(horizontal = 10f, vertical = -5f),
            droit = MirrorPreferences(horizontal = 12f, vertical = -4f),
            interieur = MirrorPreferences(horizontal = 2f, vertical = 3f),
        ),
        climate = ClimatePreferences(temperatureCelsius = 21f, vitesseVentilation = 2f),
    )

    val driver2 = DriverPreferences(
        seat = SeatPreferences(positionAvantArriere = 60f, hauteur = 40f, inclinaisonDossier = 105f),
        steeringWheel = SteeringWheelPreferences(hauteur = 55f, profondeur = 45f),
        mirrors = MirrorsPreferences(
            gauche = MirrorPreferences(horizontal = 8f, vertical = -3f),
            droit = MirrorPreferences(horizontal = 9f, vertical = -2f),
            interieur = MirrorPreferences(horizontal = -1f, vertical = 1f),
        ),
        climate = ClimatePreferences(temperatureCelsius = 19f, vitesseVentilation = 3f),
    )

    val availableDrivers = listOf("driver_1", "driver_2")

    fun preferencesFor(driverName: String?): DriverPreferences? = when (driverName) {
        "driver_1" -> driver1
        "driver_2" -> driver2
        else -> null
    }
}

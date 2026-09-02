package com.pfa.driverui.data

import com.pfa.driverui.model.DriverPreferences
import com.pfa.driverui.model.MirrorPreferences
import com.pfa.driverui.model.MirrorsPreferences
import com.pfa.driverui.model.SeatPreferences

/**
 * Données factices pour développer/tester l'interface AVANT de brancher
 * l'API réseau (api_server.py). Reprend telles quelles les valeurs
 * actuellement présentes dans preferences.json côté serveur, pour que
 * la démo visuelle soit cohérente avec les vraies données du projet.
 *
 * À remplacer par de vrais appels réseau (REST ou WebSocket) une fois
 * l'UI validée visuellement — voir la suite du projet.
 */
object MockPreferencesRepository {

    val driver1 = DriverPreferences(
        seat = SeatPreferences(
            positionAvantArriere = 45f,
            hauteur = 30f,
            inclinaisonDossier = 100f,
        ),
        mirrors = MirrorsPreferences(
            gauche = MirrorPreferences(horizontal = 10f, vertical = -5f),
            droit = MirrorPreferences(horizontal = 12f, vertical = -4f),
            interieur = MirrorPreferences(horizontal = 2f, vertical = 3f),
        ),
    )

    val driver2 = DriverPreferences(
        seat = SeatPreferences(
            positionAvantArriere = 60f,
            hauteur = 40f,
            inclinaisonDossier = 105f,
        ),
        mirrors = MirrorsPreferences(
            gauche = MirrorPreferences(horizontal = 8f, vertical = -3f),
            droit = MirrorPreferences(horizontal = 9f, vertical = -2f),
            interieur = MirrorPreferences(horizontal = -1f, vertical = 1f),
        ),
    )

    /** Simule la liste des conducteurs enrôlés, comme le retournerait GET /drivers. */
    val availableDrivers = listOf("driver_1", "driver_2")

    fun preferencesFor(driverName: String?): DriverPreferences? = when (driverName) {
        "driver_1" -> driver1
        "driver_2" -> driver2
        else -> null
    }
}

package com.pfa.driverui.ui

import com.pfa.driverui.model.DriverPreferences

sealed class AppScreen {
    object Listening : AppScreen()
    data class Identified(val driverName: String, val preferences: DriverPreferences) : AppScreen()
    data class ShowingPreferences(val driverName: String, val preferences: DriverPreferences) : AppScreen()

    /** [driverName]/[preferences] = the driver who was identified when settings was opened (null from the listening screen). */
    data class Settings(val driverName: String?, val preferences: DriverPreferences?) : AppScreen()

    /** Debug tool (long-press the gear): no identification, free camera, movable tap markers. */
    object Debug : AppScreen()
}

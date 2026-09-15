package com.pfa.driverui.ui

import com.pfa.driverui.model.DriverPreferences

sealed class AppScreen {
    object Listening : AppScreen()
    data class Identified(val driverName: String, val preferences: DriverPreferences) : AppScreen()
    data class ShowingPreferences(val driverName: String, val preferences: DriverPreferences) : AppScreen()
    object Settings : AppScreen()
}

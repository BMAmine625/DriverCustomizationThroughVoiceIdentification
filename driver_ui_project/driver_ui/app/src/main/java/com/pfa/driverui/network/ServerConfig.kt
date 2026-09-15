package com.pfa.driverui.network

import android.content.Context
import android.content.SharedPreferences

/**
 * Stocke l'adresse du serveur API (host:port) de façon persistante,
 * pour ne pas avoir à la retaper à chaque lancement.
 *
 * Par défaut : 10.0.2.2:8000, l'alias spécial que l'émulateur Android
 * utilise pour joindre le localhost de la machine hôte. À changer dans
 * les réglages pour l'adresse IP réelle du serveur sur un appareil
 * physique.
 */
object ServerConfig {
    private const val PREFS_NAME = "driver_ui_settings"
    private const val KEY_HOST = "server_host"
    private const val DEFAULT_HOST = "10.0.2.2:8000"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHost(context: Context): String =
        prefs(context).getString(KEY_HOST, DEFAULT_HOST) ?: DEFAULT_HOST

    fun setHost(context: Context, host: String) {
        prefs(context).edit().putString(KEY_HOST, host).apply()
    }
}

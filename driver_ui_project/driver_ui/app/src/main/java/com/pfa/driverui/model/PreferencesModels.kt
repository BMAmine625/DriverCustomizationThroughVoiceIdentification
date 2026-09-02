package com.pfa.driverui.model

/**
 * Reflète exactement la structure de préférences définie côté serveur
 * (preferences.json / _schema) : siège + 3 rétroviseurs (gauche, droit,
 * intérieur), chacun avec ses deux axes horizontal/vertical.
 *
 * Garder ces modèles alignés avec le schéma Python évite toute
 * divergence entre client et serveur — si le schéma change côté
 * serveur, ces classes devront être mises à jour en conséquence (pas
 * de génération automatique pour l'instant, à faire manuellement).
 */

data class SeatPreferences(
    val positionAvantArriere: Float, // %, 0-100
    val hauteur: Float,              // %, 0-100
    val inclinaisonDossier: Float,   // degrés, 90-160
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

data class DriverPreferences(
    val seat: SeatPreferences,
    val mirrors: MirrorsPreferences,
)

/** Valeurs par défaut neutres, utilisées tant qu'aucun conducteur n'est identifié. */
val DEFAULT_PREFERENCES = DriverPreferences(
    seat = SeatPreferences(positionAvantArriere = 50f, hauteur = 50f, inclinaisonDossier = 110f),
    mirrors = MirrorsPreferences(
        gauche = MirrorPreferences(horizontal = 0f, vertical = 0f),
        droit = MirrorPreferences(horizontal = 0f, vertical = 0f),
        interieur = MirrorPreferences(horizontal = 0f, vertical = 0f),
    ),
)

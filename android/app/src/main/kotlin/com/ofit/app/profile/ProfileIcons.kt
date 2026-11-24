package com.ofit.app.profile

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.ui.graphics.vector.ImageVector

/** Icons a profile can wear instead of its initial (a set like Nuvio's avatars, from Material icons). */
object ProfileIcons {
    val all: List<Pair<String, ImageVector>> = listOf(
        "run" to Icons.AutoMirrored.Filled.DirectionsRun,
        "walk" to Icons.AutoMirrored.Filled.DirectionsWalk,
        "bike" to Icons.AutoMirrored.Filled.DirectionsBike,
        "fitness" to Icons.Filled.FitnessCenter,
        "heart" to Icons.Filled.Favorite,
        "bolt" to Icons.Filled.Bolt,
        "fire" to Icons.Filled.LocalFireDepartment,
        "trophy" to Icons.Filled.EmojiEvents,
        "star" to Icons.Filled.Star,
        "mountain" to Icons.Filled.Terrain,
        "leaf" to Icons.Filled.Eco,
        "water" to Icons.Filled.WaterDrop,
        "sun" to Icons.Filled.WbSunny,
        "moon" to Icons.Filled.DarkMode,
        "rocket" to Icons.Filled.RocketLaunch,
        "music" to Icons.Filled.MusicNote,
        "coffee" to Icons.Filled.LocalCafe,
        "spa" to Icons.Filled.Spa,
        "pets" to Icons.Filled.Pets,
        "face" to Icons.Filled.Face,
    )

    fun of(key: String?): ImageVector? = all.firstOrNull { it.first == key }?.second
}

/** A profile's health details and units, edited with the profile (they feed zones and calories). */
data class HealthDetails(
    val ageYears: Int? = null,
    val maxHr: Int? = null,
    val weightKg: Double? = null,
    val sex: com.ofit.protocol.Sex? = null,
    val units: com.ofit.protocol.UnitSystem = com.ofit.protocol.UnitSystem.METRIC,
)

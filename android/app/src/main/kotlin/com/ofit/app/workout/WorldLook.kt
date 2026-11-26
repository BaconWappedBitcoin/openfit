package com.ofit.app.workout

import android.content.Context
import kotlin.random.Random

/**
 * How the 3D trail looks (user request 2026-11-25): the landscape, the time of day and the
 * weather, each picked before Start or left to "Surprise me". Keys match the trail page's
 * parameters (assets/world/world.js).
 */
data class WorldLook(val land: String, val time: String, val weather: String) {
    companion object {
        const val SURPRISE = "surprise"
        /** The nine biomes, named as on the Virtual trails screen ([com.ofit.protocol.TrailProgram]). */
        val LANDS = com.ofit.protocol.TrailProgram.TRAILS.map { it.key to it.name }
        val TIMES = listOf("morning" to "Morning", "day" to "Day", "sunset" to "Sunset", "night" to "Night")
        val WEATHERS = listOf("clear" to "Clear", "overcast" to "Overcast", "fog" to "Fog", "rain" to "Rain", "snow" to "Snow")

        private const val PREFS = "ofit_videos"

        /** The runner's choices, each a key or [SURPRISE] (the default). */
        fun choices(context: Context): WorldLook {
            val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            return WorldLook(
                p.getString("world_land", SURPRISE) ?: SURPRISE,
                p.getString("world_time", SURPRISE) ?: SURPRISE,
                p.getString("world_weather", SURPRISE) ?: SURPRISE,
            )
        }

        fun save(context: Context, look: WorldLook) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString("world_land", look.land).putString("world_time", look.time).putString("world_weather", look.weather).apply()
        }

        /**
         * A real look from the choices: each "Surprise me" is drawn from [random]. Clear weather is
         * the most likely surprise, and snow the least, so a surprise is mostly a nice day out.
         */
        fun resolve(choices: WorldLook, random: Random): WorldLook = WorldLook(
            land = choices.land.takeIf { it != SURPRISE } ?: LANDS.random(random).first,
            time = choices.time.takeIf { it != SURPRISE } ?: listOf("morning", "day", "day", "day", "sunset", "night").random(random),
            weather = choices.weather.takeIf { it != SURPRISE }
                ?: listOf("clear", "clear", "clear", "clear", "overcast", "overcast", "fog", "rain", "rain", "snow").random(random),
        )

        fun label(list: List<Pair<String, String>>, key: String) = list.firstOrNull { it.first == key }?.second ?: key
    }
}

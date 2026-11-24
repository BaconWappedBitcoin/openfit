package com.ofit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Accent themes: one dark base (the app is used in a gym, often while moving) with a choice of
 * accent colour, picked per profile. The pattern follows media apps such as NuvioTV; the palette
 * and code here are ofit's own.
 */
enum class OfitAccent(val label: String, val primary: Color, val container: Color) {
    LAVENDER("Lavender", Color(0xFFD0BCFF), Color(0xFF4F378B)),
    OCEAN("Ocean", Color(0xFF7CC4FA), Color(0xFF0B4F7C)),
    EMERALD("Emerald", Color(0xFF7EDC9B), Color(0xFF145A32)),
    CRIMSON("Crimson", Color(0xFFFF8A80), Color(0xFF7F1D1D)),
    AMBER("Amber", Color(0xFFFFC857), Color(0xFF6B4A00)),
    ROSE("Rose", Color(0xFFF9A8D4), Color(0xFF7A1F4F)),
    TEAL("Teal", Color(0xFF6EE7D8), Color(0xFF0F5A52)),
    GRAPHITE("Graphite", Color(0xFFE0E0E0), Color(0xFF3A3A3F)),
    ;

    companion object {
        fun fromName(name: String?): OfitAccent = entries.firstOrNull { it.name == name } ?: LAVENDER
    }
}

/** The application theme, in the chosen accent. */
@Composable
fun OfitTheme(accent: OfitAccent = OfitAccent.LAVENDER, content: @Composable () -> Unit) {
    val base = darkColorScheme()
    MaterialTheme(
        colorScheme = base.copy(
            primary = accent.primary,
            onPrimary = Color(0xFF111111),
            primaryContainer = accent.container,
            onPrimaryContainer = Color.White,
            secondaryContainer = accent.container,
            onSecondaryContainer = Color.White,
            background = Color(0xFF0F0F12),
            surface = Color(0xFF0F0F12),
            surfaceVariant = Color(0xFF26262C),
        ),
        content = content,
    )
}

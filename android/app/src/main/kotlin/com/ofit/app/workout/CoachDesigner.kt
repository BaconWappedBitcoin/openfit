package com.ofit.app.workout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The coach designer (user request 2026-11-24): woman or man, the outfit, its colours, skin tone
 * and hair, with the coach running in a live preview. Every change is kept at once.
 */
@Composable
fun CoachDesignerScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var choice by remember { mutableStateOf(CoachLook.choice(context).let { if (it == CoachChoice.OFF) CoachChoice.FEMALE else it }) }
    var look by remember { mutableStateOf(CoachLook.load(context)) }
    CoachDesigner(
        choice = choice,
        look = look,
        onChoice = { choice = it; CoachLook.saveChoice(context, it) },
        onLook = { look = it; CoachLook.save(context, it) },
        onDone = onBack,
        doneLabel = "Back",
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CoachDesigner(
    choice: CoachChoice,
    look: CoachLook,
    onChoice: (CoachChoice) -> Unit,
    onLook: (CoachLook) -> Unit,
    onDone: () -> Unit,
    doneLabel: String = "Done",
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.colorScheme.primary.toArgb() and 0xFFFFFF
    var preview by remember { mutableStateOf(8.0) } // km/h: a jog
    Row(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Preview: the coach running on a simple path, at a walk, jog or run.
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(Brush.verticalGradient(0f to Color(0xFF6E93B8), 0.55f to Color(0xFFB8CCDD), 0.551f to Color(0xFF7C8466), 1f to Color(0xFF5A6048))),
            contentAlignment = Alignment.BottomCenter,
        ) {
            CoachFigure(
                choice = if (choice == CoachChoice.OFF) CoachChoice.FEMALE else choice,
                look = look,
                speedKph = { preview },
                running = { true },
                modifier = Modifier.size(width = 420.dp, height = 532.dp).padding(bottom = 40.dp),
            )
            Row(
                Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf("Walk" to 4.8, "Jog" to 8.0, "Run" to 12.0).forEach { (label, kph) ->
                    FilterChip(selected = preview == kph, onClick = { preview = kph }, label = { Text(label) })
                }
            }
        }
        // Choices.
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState())
                .statusBarsPadding()
                .padding(28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Coach designer", fontSize = 30.sp, fontWeight = FontWeight.Bold)
                    Text("Changes are saved as you go.", color = Color(0x99FFFFFF))
                }
                OutlinedButton(onClick = onDone) { Text(doneLabel) }
            }

            Section("Coach") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(CoachChoice.FEMALE to "Woman", CoachChoice.MALE to "Man").forEach { (c, label) ->
                        FilterChip(selected = choice == c, onClick = { onChoice(c) }, label = { Text(label) })
                    }
                }
            }
            Section("Outfit") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CoachLook.Style.entries.forEach { s ->
                        FilterChip(selected = look.style == s, onClick = { onLook(look.copy(style = s)) }, label = { Text(s.label) })
                    }
                }
            }
            // The race kit's singlet stays white: this colour is its band and, on Auto, its shorts.
            Section(if (look.style == CoachLook.Style.RACE) "Kit colour" else "Top") {
                Swatches(
                    colours = CoachLook.CLOTH_COLOURS,
                    selected = look.top,
                    first = Triple("Theme", theme, look.top == null),
                    onFirst = { onLook(look.copy(top = null)) },
                    onPick = { onLook(look.copy(top = it)) },
                )
            }
            Section(if (look.style == CoachLook.Style.TIGHTS) "Tights" else "Shorts") {
                Swatches(
                    colours = CoachLook.CLOTH_COLOURS,
                    selected = look.bottom,
                    first = Triple("Auto", if (look.style == CoachLook.Style.RACE) (look.top ?: theme) else 0x24272E, look.bottom == null),
                    onFirst = { onLook(look.copy(bottom = null)) },
                    onPick = { onLook(look.copy(bottom = it)) },
                )
            }
            Section("Skin tone") {
                Swatches(colours = CoachLook.SKIN_TONES, selected = look.skin, onPick = { onLook(look.copy(skin = it)) })
            }
            Section("Hair") {
                Swatches(colours = CoachLook.HAIR_COLOURS, selected = look.hair, onPick = { onLook(look.copy(hair = it)) })
            }
            OutlinedButton(onClick = { onLook(CoachLook()) }) { Text("Reset to the standard look") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0x99FFFFFF))
        content()
    }
}

/** Round colour swatches; [first] is an extra labelled one (Theme, Auto) shown before the rest. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Swatches(
    colours: List<Int>,
    selected: Int?,
    onPick: (Int) -> Unit,
    first: Triple<String, Int, Boolean>? = null,
    onFirst: () -> Unit = {},
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (first != null) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Swatch(first.second, first.third, onFirst)
                Text(first.first, fontSize = 11.sp, color = Color(0x99FFFFFF))
            }
        }
        colours.forEach { c -> Swatch(c, first?.third != true && selected == c, { onPick(c) }) }
    }
}

@Composable
private fun Swatch(rgb: Int, on: Boolean, onClick: () -> Unit) {
    val colour = Color(0xFF000000.toInt() or rgb)
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(colour)
            .border(if (on) 3.dp else 1.dp, if (on) Color.White else Color(0x33FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (on) {
            val dark = (colour.red * 0.3f + colour.green * 0.59f + colour.blue * 0.11f) > 0.6f
            Icon(Icons.Filled.Check, contentDescription = null, tint = if (dark) Color.Black else Color.White)
        }
    }
}

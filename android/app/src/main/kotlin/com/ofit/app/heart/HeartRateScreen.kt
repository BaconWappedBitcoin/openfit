package com.ofit.app.heart

import com.ofit.app.ui.MenuButton
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ofit.protocol.HeartRateSource
import com.ofit.protocol.HeartRateZones
import com.ofit.transport.HeartRateMonitor

/** The colour of each zone 0-5: grey, blue, green, yellow, orange, red (the usual watch colours). */
fun zoneColor(zone: Int?): Color = when (zone) {
    1 -> Color(0xFF64B5F6)
    2 -> Color(0xFF66BB6A)
    3 -> Color(0xFFFFD54F)
    4 -> Color(0xFFFF9800)
    5 -> Color(0xFFF44336)
    else -> Color.White
}

/**
 * Settings > Heart-rate monitor (spec 002, H1 and H5): find a strap, choose it, forget it, and set
 * the age or max heart rate the zones come from.
 */
@Composable
fun HeartRateScreen(manager: HeartRateManager, onBack: () -> Unit, embedded: Boolean = false) {
    val devices by manager.devices.collectAsStateWithLifecycle()
    val scanning by manager.scanning.collectAsStateWithLifecycle()
    val savedName by manager.savedName.collectAsStateWithLifecycle()
    val link by manager.monitor.link.collectAsStateWithLifecycle()
    val shown by manager.shown.collectAsStateWithLifecycle()
    val maxHr by manager.maxHr.collectAsStateWithLifecycle()
    val age by manager.age.collectAsStateWithLifecycle()

    var ageText by remember(age) { mutableStateOf(age?.toString() ?: "") }
    var maxText by remember(maxHr) { mutableStateOf(maxHr?.toString() ?: "") }

    // Embedded in Devices: no header of its own, just the strap side.
    Column(if (embedded) Modifier.fillMaxSize() else Modifier.fillMaxSize().statusBarsPadding().padding(16.dp)) {
        if (!embedded) {
            com.ofit.app.ui.ScreenHeader("Heart-rate monitor", "Your strap, zones and Hold zone", onBack = { manager.stopScan(); onBack() })
            Spacer(Modifier.height(12.dp))
        } else {
            Text("HEART-RATE STRAP", style = MaterialTheme.typography.labelLarge, color = Color(0x99FFFFFF))
            Spacer(Modifier.height(8.dp))
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                if (savedName == null) {
                    Text("No strap chosen. Any Bluetooth heart-rate strap or a watch in \"broadcast heart rate\" mode works.")
                } else {
                    val linkText = when (link) {
                        HeartRateMonitor.LinkState.CONNECTED -> "connected"
                        HeartRateMonitor.LinkState.CONNECTING -> "connecting…"
                        HeartRateMonitor.LinkState.LOST -> "out of range; reconnects by itself"
                        HeartRateMonitor.LinkState.IDLE -> "not connected"
                    }
                    Text("$savedName — $linkText", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            shown.bpm?.toString() ?: "--",
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (shown.bpm != null) zoneColor(shown.zone) else MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(Modifier.width(8.dp))
                        val from = when (shown.source) {
                            HeartRateSource.STRAP -> "bpm from the strap"
                            HeartRateSource.CONSOLE -> "bpm from the treadmill's grips"
                            HeartRateSource.NONE -> "no reading yet"
                        }
                        Text(from + (shown.zone?.let { " · zone $it" } ?: ""))
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = manager::forget) { Text("Forget this strap") }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Zones", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Your age, max heart rate, weight and sex are in your profile (tap your avatar, then Edit profiles).",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    val zones = HeartRateZones.of(maxHr, age)
                    Text(
                        if (zones == null) "No zones yet" else
                            (1..5).joinToString("  ") { "Z$it ${zones.lowerBound(it)}+" },
                    )
                }
            }
        }


        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Hold zone: how hard to push", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Gentle: tiny steps, 5-min warm-up, at most +0.5 km/h. Normal: +1 km/h, 3-min warm-up. " +
                        "Strong: +1.5 km/h, 2-min warm-up. None of them ever pushes during the warm-up.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                val level by manager.coachLevel.collectAsStateWithLifecycle()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.ofit.protocol.HeartRateCoach.Aggressiveness.entries.forEach { option ->
                        androidx.compose.material3.FilterChip(
                            selected = option == level,
                            onClick = { manager.setCoachLevel(option) },
                            label = { Text(option.label) },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { if (scanning) manager.stopScan() else manager.startScan() }) {
                Text(if (scanning) "Stop looking" else "Find a strap")
            }
            Spacer(Modifier.width(12.dp))
            if (scanning) Text("Looking for heart-rate straps… wear it so it wakes up.")
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(devices, key = { it.address }) { device ->
                Card(
                    Modifier
                        .fillMaxWidth()
                        .clickable { manager.choose(device) },
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            (if (device.isHeartRate) "♥ " else "") + device.name,
                            Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            color = if (device.isHeartRate) MaterialTheme.colorScheme.onSurface else Color(0x99FFFFFF),
                        )
                        Text(
                            (if (device.isHeartRate) "heart rate · " else "other device · ") + "signal ${device.rssi} dBm",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

/** Weight and sex for the calorie estimate (age is in Zones above). */
@Composable
private fun AboutYouCard(manager: HeartRateManager) {
    val units = com.ofit.app.settings.LocalUnits.current
    val imperial = units == com.ofit.protocol.UnitSystem.IMPERIAL
    val weightKg by manager.weightKg.collectAsStateWithLifecycle()
    val sex by manager.sex.collectAsStateWithLifecycle()
    var weightText by remember(weightKg, imperial) {
        mutableStateOf(weightKg?.let { "%.0f".format(if (imperial) it * 2.20462 else it) } ?: "")
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("About you (for calories)", style = MaterialTheme.typography.titleMedium)
            Text(
                "Calories are estimated from speed, incline and your weight. With a heart-rate strap, age, sex " +
                    "and weight set, your heart rate is used too, which is closer for most people. Without a weight, " +
                    "70 kg is assumed. All of it stays on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = {
                        weightText = it.filter(Char::isDigit).take(3)
                        val entered = weightText.toDoubleOrNull()
                        manager.setWeightKg(entered?.let { w -> if (imperial) w / 2.20462 else w })
                    },
                    label = { Text(if (imperial) "Weight (lb)" else "Weight (kg)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(150.dp),
                )
                listOf(com.ofit.protocol.Sex.FEMALE to "Female", com.ofit.protocol.Sex.MALE to "Male", null to "Not given")
                    .forEach { (option, label) ->
                        androidx.compose.material3.FilterChip(
                            selected = sex == option,
                            onClick = { manager.setSex(option) },
                            label = { Text(label) },
                        )
                    }
            }
        }
    }
}

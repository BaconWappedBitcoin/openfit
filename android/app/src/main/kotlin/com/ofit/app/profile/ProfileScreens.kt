package com.ofit.app.profile

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.verticalScroll
import com.ofit.app.ui.theme.OfitAccent

/** A round avatar in the profile's colour (its theme), with its icon or initial. */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(profile.accent.container),
        contentAlignment = Alignment.Center,
    ) {
        val icon = ProfileIcons.of(profile.icon)
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = profile.accent.primary, modifier = Modifier.size(size * 0.55f))
        } else {
            Text(profile.initial, color = profile.accent.primary, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * "Who's running?" Shown at start-up when there is more than one profile, and from the avatar on
 * the home screen. Layout after NuvioMobile's profile picker: centred title, large round avatars on
 * 150 dp cards, an add card, and an "Edit profiles" pill that turns taps into edits.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileSelectionScreen(
    profiles: List<Profile>,
    current: Profile,
    onSelect: (Profile) -> Unit,
    onAdd: (name: String, color: Long, accent: OfitAccent, icon: String?) -> Unit,
    onUpdate: (Profile) -> Unit,
    onRemove: (Profile, Boolean) -> Unit,
    /** The profile in use's health details and units, edited with it. */
    health: HealthDetails = HealthDetails(),
    onHealth: (HealthDetails) -> Unit = {},
) {
    var editMode by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }

    // The menu stays reachable here too (top left), like every other screen.
    // A soft glow in the theme colour behind the picker (Lane 1b "exciting screen" pass).
    val glow = MaterialTheme.colorScheme.primary
    androidx.compose.foundation.layout.Box(
        Modifier
            .fillMaxSize()
            .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(glow.copy(alpha = 0.20f), Color.Transparent), radius = 1400f)),
    ) {
    androidx.compose.foundation.layout.Box(Modifier.statusBarsPadding().padding(16.dp)) { com.ofit.app.ui.MenuButton() }
    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("OpenFit", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(12.dp))
        Text(
            if (editMode) "Edit profiles" else "Who's running?",
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(48.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            profiles.forEach { profile ->
                ProfileCard(
                    profile = profile,
                    selected = profile.id == current.id,
                    editMode = editMode,
                    onClick = { if (editMode) editing = profile else onSelect(profile) },
                )
            }
            if (profiles.size < MAX_PROFILES) {
                AddProfileCard(onClick = { adding = true })
            }
        }
        Spacer(Modifier.height(48.dp))
        Text(
            if (editMode) "Done" else "Edit profiles",
            color = if (editMode) MaterialTheme.colorScheme.primary else Color(0xCCFFFFFF),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(if (editMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color(0x14FFFFFF))
                .border(
                    BorderStroke(1.dp, if (editMode) MaterialTheme.colorScheme.primary.copy(alpha = 0.4f) else Color(0x33FFFFFF)),
                    RoundedCornerShape(24.dp),
                )
                .clickable { editMode = !editMode }
                .padding(horizontal = 24.dp, vertical = 10.dp),
        )
    }

    editing?.let { profile ->
        ProfileEditDialog(
            title = "Edit profile",
            initial = profile,
            canRemove = profiles.size > 1,
            onSave = { name, color, accent, icon, newHealth ->
                onUpdate(profile.copy(name = name.trim().ifBlank { profile.name }, color = color, accent = accent, icon = icon))
                newHealth?.let(onHealth)
                editing = null
            },
            health = if (profile.id == current.id) health else null,
            onRemove = { deleteData -> onRemove(profile, deleteData); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (adding) {
        ProfileEditDialog(
            title = "New profile",
            initial = Profile(0, "", ProfileManager.AVATAR_COLORS[profiles.size % ProfileManager.AVATAR_COLORS.size], OfitAccent.LAVENDER),
            canRemove = false,
            onSave = { name, color, accent, icon, _ -> onAdd(name, color, accent, icon); adding = false },
            onRemove = { _ -> },
            onDismiss = { adding = false },
        )
    }
    }
}

@Composable
private fun ProfileCard(profile: Profile, selected: Boolean, editMode: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(186.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                // A glow and a ring in the profile's own colour.
                Box(
                    Modifier
                        .size(186.dp)
                        .clip(CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(profile.accent.primary.copy(alpha = 0.45f), Color.Transparent)))
                        .border(BorderStroke(4.dp, profile.accent.primary), CircleShape),
                )
            }
            ProfileAvatar(profile, 156.dp)
            if (editMode) {
                Box(
                    Modifier.size(156.dp).clip(CircleShape).background(Color(0x88000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}", tint = Color.White, modifier = Modifier.size(44.dp))
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(profile.name, fontSize = 24.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(210.dp)
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(186.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(156.dp)
                    .clip(CircleShape)
                    .background(Color(0x0FFFFFFF))
                    .border(BorderStroke(2.dp, Color(0x55FFFFFF)), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(56.dp))
            }
        }
        Spacer(Modifier.height(14.dp))
        Text("Add profile", fontSize = 24.sp, color = Color(0xCCFFFFFF))
    }
}

/**
 * A profile: name, icon, colour (which is also the app's theme colour for this profile), and for the
 * profile in use its health details and units (they feed heart-rate zones and calories).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditDialog(
    title: String,
    initial: Profile,
    canRemove: Boolean,
    onSave: (name: String, color: Long, accent: OfitAccent, icon: String?, health: HealthDetails?) -> Unit,
    onRemove: (deleteData: Boolean) -> Unit,
    onDismiss: () -> Unit,
    /** Null when editing a profile that is not in use (its health details are edited from it). */
    health: HealthDetails? = null,
) {
    var name by remember { mutableStateOf(initial.name) }
    var accent by remember { mutableStateOf(initial.accent) }
    var icon by remember { mutableStateOf(initial.icon) }
    var confirmRemove by remember { mutableStateOf(false) }
    var deleteData by remember { mutableStateOf(false) }
    var units by remember { mutableStateOf(health?.units ?: com.ofit.protocol.UnitSystem.METRIC) }
    val imperial = units == com.ofit.protocol.UnitSystem.IMPERIAL
    var ageText by remember { mutableStateOf(health?.ageYears?.toString() ?: "") }
    var maxText by remember { mutableStateOf(health?.maxHr?.toString() ?: "") }
    var weightText by remember {
        mutableStateOf(health?.weightKg?.let { "%.0f".format(if (imperial) it * 2.20462 else it) } ?: "")
    }
    var sex by remember { mutableStateOf(health?.sex) }

    fun currentHealth(): HealthDetails? = health?.let {
        HealthDetails(
            ageYears = ageText.toIntOrNull()?.takeIf { a -> a in 10..100 },
            maxHr = maxText.toIntOrNull()?.takeIf { m -> m in 100..230 },
            weightKg = weightText.toDoubleOrNull()?.let { w -> if (imperial) w / 2.20462 else w }?.takeIf { w -> w in 25.0..300.0 },
            sex = sex,
            units = units,
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProfileAvatar(initial.copy(name = name.ifBlank { "?" }, accent = accent, icon = icon), 64.dp)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                }
                Text("Colour (also the app's theme colour)", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OfitAccent.entries.forEach { a ->
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(a.container)
                                .border(BorderStroke(if (a == accent) 3.dp else 1.dp, if (a == accent) Color.White else a.primary), CircleShape)
                                .clickable { accent = a },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(Modifier.size(18.dp).clip(CircleShape).background(a.primary))
                        }
                    }
                }
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // The initial, then the icons.
                    IconChoice(selected = icon == null, accent = accent, onClick = { icon = null }) {
                        Text(name.trim().firstOrNull()?.uppercase() ?: "A", color = accent.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    ProfileIcons.all.forEach { (key, vector) ->
                        IconChoice(selected = icon == key, accent = accent, onClick = { icon = key }) {
                            Icon(vector, contentDescription = key, tint = accent.primary, modifier = Modifier.size(24.dp))
                        }
                    }
                }
                if (health != null) {
                    Text("Units (speed, distance and weight across the app)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(com.ofit.protocol.UnitSystem.IMPERIAL to "mph, miles, lb", com.ofit.protocol.UnitSystem.METRIC to "km/h, km, kg").forEach { (option, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = units == option,
                                onClick = {
                                    // Keep the same weight when switching units.
                                    val kg = weightText.toDoubleOrNull()?.let { w -> if (imperial) w / 2.20462 else w }
                                    units = option
                                    weightText = kg?.let { w -> "%.0f".format(if (option == com.ofit.protocol.UnitSystem.IMPERIAL) w * 2.20462 else w) } ?: ""
                                },
                                label = { Text(label) },
                            )
                        }
                    }
                    Text("About you (zones and calories)", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = ageText,
                            onValueChange = { ageText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Age") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.width(100.dp),
                        )
                        OutlinedTextField(
                            value = weightText,
                            onValueChange = { weightText = it.filter(Char::isDigit).take(3) },
                            label = { Text(if (imperial) "Weight (lb)" else "Weight (kg)") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.width(140.dp),
                        )
                        OutlinedTextField(
                            value = maxText,
                            onValueChange = { maxText = it.filter(Char::isDigit).take(3) },
                            label = { Text("Max HR") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
                            modifier = Modifier.width(110.dp),
                        )
                    }
                    Text(
                        "Zones use your max heart rate, or 220 minus your age. Leave anything blank to skip it. It stays on this tablet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0x99FFFFFF),
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(com.ofit.protocol.Sex.FEMALE to "Female", com.ofit.protocol.Sex.MALE to "Male", null to "Not given").forEach { (option, label) ->
                            androidx.compose.material3.FilterChip(selected = sex == option, onClick = { sex = option }, label = { Text(label) })
                        }
                    }
                } else {
                    Text(
                        "Age, weight and heart-rate details are edited while this profile is the one in use.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0x99FFFFFF),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(name, accent.container.toArgb().toLong() and 0xFFFFFFFFL, accent, icon, currentHealth())
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (canRemove) {
                    TextButton(onClick = { confirmRemove = true }) { Text("Delete profile", color = Color(0xFFF87171)) }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Delete ${initial.name}?") },
            text = {
                Column {
                    Text("The profile goes from the list.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.material3.Checkbox(checked = deleteData, onCheckedChange = { deleteData = it })
                        Text("Also delete its history, strap and settings (can't be undone)")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { confirmRemove = false; onRemove(deleteData) }) { Text("Delete", color = Color(0xFFF87171)) } },
            dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun IconChoice(selected: Boolean, accent: OfitAccent, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(accent.container)
            .border(BorderStroke(if (selected) 3.dp else 0.dp, Color.White), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { content() }
}

private const val MAX_PROFILES = 6

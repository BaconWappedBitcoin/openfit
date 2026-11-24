package com.ofit.app.ui
import androidx.compose.runtime.collectAsState
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.automirrored.filled.ArrowBack

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ofit.app.profile.Profile
import com.ofit.app.profile.ProfileAvatar
import com.ofit.app.ui.theme.OfitMenuColors
import com.ofit.app.ui.theme.OfitMenuSegments
import com.ofit.app.workout.AppScreen
import com.ofit.protocol.UnitSystem

/**
 * The hamburger menu, in OpenFit's style: an accent-gradient header with who is running, grouped
 * places with tinted icons, and the small settings as segmented controls.
 */
@Composable
fun AppDrawerContent(
    current: AppScreen,
    profile: Profile,
    units: UnitSystem,
    fullscreen: Boolean,
    onGo: (AppScreen) -> Unit,
    onUnits: (UnitSystem) -> Unit,
    onFullscreen: (Boolean) -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    ModalDrawerSheet(
        modifier = Modifier.width(360.dp),
        drawerContainerColor = Color(0xFF111116),
        drawerShape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
    ) {
        Column(Modifier.fillMaxHeight().verticalScroll(rememberScrollState())) {
            // Header: wordmark and who is running, over the profile's accent.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primaryContainer, Color(0xFF111116))))
                    .padding(start = 24.dp, end = 20.dp, top = 28.dp, bottom = 20.dp),
            ) {
                Column {
                    Text("OpenFit", color = accent, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("Open treadmill control", color = OfitMenuColors.muted, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0x1FFFFFFF))
                            .clickable { onGo(AppScreen.Profiles) }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ProfileAvatar(profile, 52.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(profile.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                            Text("${profile.accent.label} theme · ${if (units == UnitSystem.METRIC) "km/h" else "mph"}", color = OfitMenuColors.muted, fontSize = 12.sp)
                        }
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = OfitMenuColors.muted)
                    }
                }
            }

            Section("Train")
            Item("Home", Icons.Filled.Home, current == AppScreen.Home) { onGo(AppScreen.Home) }
            Item("Quick start", Icons.Filled.Bolt, current == AppScreen.QuickStart) { onGo(AppScreen.QuickStart) }
            Item("Browse", Icons.Filled.Movie, current == AppScreen.Browse) { onGo(AppScreen.Browse) }
            Item("My workouts", Icons.AutoMirrored.Filled.List, current == AppScreen.Workouts) { onGo(AppScreen.Workouts) }
            Item("Manual control", Icons.Filled.Tune, current == AppScreen.ManualControl) { onGo(AppScreen.ManualControl) }

            Section("Discover")
            Item("Find a video", Icons.Filled.Search, current == AppScreen.VideoSearch) { onGo(AppScreen.VideoSearch) }
            Item("Community library", Icons.Filled.Groups, current == AppScreen.Library) { onGo(AppScreen.Library) }

            Section("You")
            Item("Training plans", Icons.Filled.CalendarMonth, current == AppScreen.Plans) { onGo(AppScreen.Plans) }
            Item("History", Icons.Filled.History, current == AppScreen.History) { onGo(AppScreen.History) }
            Item("Devices", Icons.Filled.Bluetooth, current == AppScreen.HeartRate) { onGo(AppScreen.HeartRate) }
            Item("Profiles", Icons.Filled.People, current == AppScreen.Profiles) { onGo(AppScreen.Profiles) }
            Item("Coach designer", Icons.AutoMirrored.Filled.DirectionsRun, current == AppScreen.CoachDesigner) { onGo(AppScreen.CoachDesigner) }

            Item("Keys & services", Icons.Filled.Key, current == AppScreen.AiKey) { onGo(AppScreen.AiKey) }
            Item("About & legal", Icons.Filled.Info, current == AppScreen.Legal) { onGo(AppScreen.Legal) }

            Section("Settings")
            BackupItems()
            HealthConnectItem()
            // Units (km/h or mph) are part of each profile now: Profiles, then edit (user request, 2026-11-24).
            Row(
                Modifier.padding(start = 24.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Fullscreen", color = OfitMenuColors.text, fontSize = 15.sp)
                    Text("Hide the status bar and taskbar", color = OfitMenuColors.muted, fontSize = 12.sp)
                }
                Switch(
                    checked = fullscreen,
                    onCheckedChange = onFullscreen,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF111111),
                        checkedTrackColor = accent,
                        uncheckedThumbColor = OfitMenuColors.muted,
                        uncheckedTrackColor = Color(0x22FFFFFF),
                        uncheckedBorderColor = Color(0x33FFFFFF),
                    ),
                )
            }
        }
    }
}

/**
 * Backup (user request 2026-11-24): save everything to one file anywhere Android's picker reaches,
 * Google Drive included, and restore it on a new tablet. Android's own backup to Drive runs too.
 */
@Composable
private fun BackupItems() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var pendingRestore by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<android.net.Uri?>(null) }
    fun toast(text: String) = android.widget.Toast.makeText(context, text, android.widget.Toast.LENGTH_LONG).show()
    val save = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument(com.ofit.app.settings.BackupFiles.MIME),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val result = runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { out ->
                    com.ofit.app.settings.BackupFiles.write(
                        com.ofit.app.settings.BackupFiles.prefsDir(context), context.filesDir, out,
                        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty(),
                    )
                }
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                result.onSuccess { toast("Backup saved ($it files). Saved keys are not included: add them again after a restore.") }
                    .onFailure { toast("The backup could not be saved: ${it.message}") }
            }
        }
    }
    val open = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) pendingRestore = uri }

    Item("Save a backup", Icons.Filled.CloudUpload, false) { save.launch(com.ofit.app.settings.BackupFiles.suggestedName()) }
    Item("Restore a backup", Icons.Filled.CloudDownload, false) {
        if (com.ofit.app.settings.BackupFiles.workoutRunning(context)) toast("Finish the workout first: restoring restarts OpenFit.")
        else open.launch(arrayOf(com.ofit.app.settings.BackupFiles.MIME, "application/octet-stream"))
    }
    pendingRestore?.let { uri ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore this backup?") },
            text = {
                Text(
                    "Profiles, settings, workouts, history and the coach on this tablet are replaced by the backup's, " +
                        "then OpenFit restarts. Saved keys stay as they are.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    pendingRestore = null
                    scope.launch(kotlinx.coroutines.Dispatchers.IO) {
                        val result = runCatching {
                            context.contentResolver.openInputStream(uri)!!.use {
                                com.ofit.app.settings.BackupFiles.restore(it, com.ofit.app.settings.BackupFiles.prefsDir(context), context.filesDir)
                            }
                        }
                        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                            result.onSuccess {
                                toast("Restored $it files. OpenFit is restarting.")
                                com.ofit.app.settings.BackupFiles.restartApp(context)
                            }.onFailure { toast("Nothing was changed: ${it.message}") }
                        }
                    }
                }) { Text("Restore") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { pendingRestore = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title.uppercase(),
        color = OfitMenuColors.muted,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.4.sp,
        modifier = Modifier.padding(start = 26.dp, top = 18.dp, bottom = 6.dp),
    )
}

@Composable
private fun Item(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(if (selected) accent else accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (selected) Color(0xFF111111) else accent, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            color = if (selected) accent else OfitMenuColors.text,
            fontSize = 16.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        )
    }
}


/** Opens the menu; null on screens where it is not offered (the workout, "Who's running?"). */
val LocalOpenMenu = androidx.compose.runtime.staticCompositionLocalOf<(() -> Unit)?> { null }

/** Back, the same on every screen: a round button with an arrow (after Nuvio's back button). */
@Composable
fun BackButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .size(48.dp)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClickLabel = "Back", onClick = onClick),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Icon(
            androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = "Back",
            modifier = Modifier.size(24.dp),
        )
    }
}

/** The ☰ button for a screen's header; draws nothing where the menu is not offered. */
@Composable
fun MenuButton() {
    val open = LocalOpenMenu.current ?: return
    androidx.compose.material3.IconButton(onClick = open, modifier = Modifier.size(56.dp)) {
        Icon(androidx.compose.material.icons.Icons.Filled.Menu, contentDescription = "Menu", modifier = Modifier.size(30.dp))
    }
}


/** Health Connect (P17): turn on writing finished workouts there, grant access, send past ones. */
@androidx.compose.runtime.Composable
private fun HealthConnectItem() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val health = (context.applicationContext as com.ofit.app.TreadmillApplication).controller.health
    val history = (context.applicationContext as com.ofit.app.TreadmillApplication).controller.history
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var open by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val enabled by health.enabled.collectAsState()
    val status by health.status.collectAsState()
    var granted by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(open) { if (open) granted = runCatching { health.hasPermissions() }.getOrDefault(false) }
    val ask = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract(),
    ) { got ->
        granted = got.containsAll(health.permissions)
        health.setEnabled(granted)
    }
    Item("Health Connect", Icons.Filled.FavoriteBorder, false) { open = true }
    if (open) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { open = false },
            title = { Text("Health Connect") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!health.available) {
                        Text("Health Connect is not available on this device.")
                    } else {
                        Text("Send each finished workout to Health Connect: walking or running, distance, calories, climb, heart rate and speed. Apps such as Fitbit and Google Fit can then show it. OpenFit only writes, it reads nothing.")
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Send workouts", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
                            androidx.compose.material3.Switch(checked = enabled && granted, onCheckedChange = { on ->
                                if (on && !granted) ask.launch(health.permissions) else health.setEnabled(on)
                            })
                        }
                        if (enabled && granted) {
                            androidx.compose.material3.OutlinedButton(onClick = {
                                scope.launch { health.send(history.entries.value) }
                            }) { Text("Send all past workouts too") }
                        }
                        status?.let { Text(it, color = Color(0xB3FFFFFF)) }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { open = false }) { Text("Done") } },
        )
    }
}

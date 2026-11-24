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
import com.ofit.app.ui.theme.OfitAccent

/** A round avatar: the profile's colour with its initial. */
@Composable
fun ProfileAvatar(profile: Profile, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(profile.color)),
        contentAlignment = Alignment.Center,
    ) {
        Text(profile.initial, color = Color.White, fontSize = (size.value * 0.42f).sp, fontWeight = FontWeight.Bold)
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
    onAdd: (name: String, color: Long, accent: OfitAccent) -> Unit,
    onUpdate: (Profile) -> Unit,
    onRemove: (Profile, Boolean) -> Unit,
) {
    var editMode by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Profile?>(null) }
    var adding by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().statusBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("OpenFit", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(18.dp))
        Text(
            if (editMode) "Edit profiles" else "Who's running?",
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(32.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
        Spacer(Modifier.height(32.dp))
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
            onSave = { name, color, accent ->
                onUpdate(profile.copy(name = name.trim().ifBlank { profile.name }, color = color, accent = accent))
                editing = null
            },
            onRemove = { deleteData -> onRemove(profile, deleteData); editing = null },
            onDismiss = { editing = null },
        )
    }
    if (adding) {
        ProfileEditDialog(
            title = "New profile",
            initial = Profile(0, "", ProfileManager.AVATAR_COLORS[profiles.size % ProfileManager.AVATAR_COLORS.size], OfitAccent.LAVENDER),
            canRemove = false,
            onSave = { name, color, accent -> onAdd(name, color, accent); adding = false },
            onRemove = { _ -> },
            onDismiss = { adding = false },
        )
    }
}

@Composable
private fun ProfileCard(profile: Profile, selected: Boolean, editMode: Boolean, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(110.dp), contentAlignment = Alignment.Center) {
            if (selected) {
                Box(
                    Modifier
                        .size(110.dp)
                        .clip(CircleShape)
                        .border(BorderStroke(3.dp, profile.accent.primary), CircleShape),
                )
            }
            ProfileAvatar(profile, 100.dp)
            if (editMode) {
                Box(
                    Modifier.size(100.dp).clip(CircleShape).background(Color(0x88000000)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}", tint = Color.White, modifier = Modifier.size(32.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(profile.name, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun AddProfileCard(onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(100.dp)
                .padding(5.dp)
                .clip(CircleShape)
                .border(BorderStroke(2.dp, Color(0x55FFFFFF)), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, tint = Color(0xCCFFFFFF), modifier = Modifier.size(40.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("Add profile", fontSize = 18.sp, color = Color(0xCCFFFFFF))
    }
}

/** Name, avatar colour and theme for a profile. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfileEditDialog(
    title: String,
    initial: Profile,
    canRemove: Boolean,
    onSave: (name: String, color: Long, accent: OfitAccent) -> Unit,
    onRemove: (deleteData: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var color by remember { mutableStateOf(initial.color) }
    var accent by remember { mutableStateOf(initial.accent) }
    var confirmRemove by remember { mutableStateOf(false) }
    var deleteData by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    ProfileAvatar(initial.copy(name = name.ifBlank { "?" }, color = color), 64.dp)
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it.take(20) },
                        label = { Text("Name") },
                        singleLine = true,
                    )
                }
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    ProfileManager.AVATAR_COLORS.forEach { c ->
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color(c))
                                .border(BorderStroke(if (c == color) 3.dp else 0.dp, Color.White), CircleShape)
                                .clickable { color = c },
                        )
                    }
                }
                Text("Theme", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OfitAccent.entries.forEach { a ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(a.container)
                                .border(BorderStroke(if (a == accent) 2.dp else 0.dp, a.primary), RoundedCornerShape(12.dp))
                                .clickable { accent = a }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(14.dp).clip(CircleShape).background(a.primary))
                            Text(a.label, color = Color.White, fontSize = 14.sp)
                            if (a == accent) Icon(Icons.Filled.Check, contentDescription = null, tint = a.primary, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color, accent) }) { Text("Save") } },
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

private const val MAX_PROFILES = 6

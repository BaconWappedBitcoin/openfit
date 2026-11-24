package com.ofit.app.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ofit.app.ui.MenuButton

/**
 * Bring your own key: video-to-workout runs on the user's own Gemini account instead of the shared
 * one (user decision 2026-11-24: new AI work needs the user's own key; no paid plan for now).
 */
@Composable
fun AiKeyScreen(secrets: SecretStore, onBack: () -> Unit) {
    val hasKey by secrets.hasGeminiKey.collectAsStateWithLifecycle()
    val hasYouTube by secrets.hasYouTubeKey.collectAsStateWithLifecycle()
    val useServer by secrets.useServer.collectAsStateWithLifecycle()
    var youtubeInput by remember { mutableStateOf("") }
    var youtubeError by remember { mutableStateOf<String?>(null) }
    var input by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val uri = LocalUriHandler.current

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        com.ofit.app.ui.ScreenHeader("Keys & services", "Your own keys, and whether OpenFit's server is used", onBack = onBack)

        // OpenFit needs no server: off, the tablet does everything with the keys below and reads the
        // community library straight from the project's public repository (user 2026-11-24).
        Card(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Use the OpenFit server", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (useServer) {
                            "On: video search, video-to-workout and the coach's facts go through OpenFit's server, " +
                                "with or without your own key. Sharing and rating workouts need it."
                        } else {
                            "Off: no server is used. Search, video-to-workout and the coach's facts run on this tablet " +
                                "with your own keys below; the community library is read straight from the project's " +
                                "public repository. Sharing and rating are not available."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xCCFFFFFF),
                    )
                }
                androidx.compose.material3.Switch(checked = useServer, onCheckedChange = { secrets.setUseServer(it) })
            }
            // Self-hosting: point OpenFit at your own copy of backend/ (everything in it is free to run yourself).
            if (useServer) {
                val serverUrl by secrets.serverUrl.collectAsStateWithLifecycle()
                var draft by remember(serverUrl) { mutableStateOf(serverUrl) }
                var bad by remember { mutableStateOf(false) }
                Column(Modifier.padding(start = 20.dp, end = 20.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Server address", fontWeight = FontWeight.SemiBold)
                    Text(
                        "Running your own copy of OpenFit's server? Put its address here. HTTPS, or HTTP on your home network.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xB3FFFFFF),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = draft,
                            onValueChange = { draft = it; bad = false },
                            singleLine = true,
                            isError = bad,
                            supportingText = if (bad) { { Text("That address cannot be used.") } } else null,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                            modifier = Modifier.weight(1f),
                        )
                        Button(onClick = { bad = !secrets.setServerUrl(draft) }, enabled = draft.trim() != serverUrl) { Text("Save") }
                        if (serverUrl != com.ofit.protocol.ServerUrl.DEFAULT) {
                            TextButton(onClick = { secrets.setServerUrl(null) }) { Text("Use OpenFit's") }
                        }
                    }
                }
            }
        }

        Card(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Turn videos into workouts on your own Google account", style = MaterialTheme.typography.titleMedium)
                Text(
                    "OpenFit uses Google's Gemini to watch a YouTube video and work out the speed and incline. " +
                        "Reading a new video needs your own key, on your own account: Google's free tier covers normal use. " +
                        "Workouts, facts and landmarks others have already made are free without a key.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Your key stays encrypted on this tablet and is sent only with requests for new AI work " +
                        "(a workout, or the coach's facts and landmarks). The server uses it for that request and never stores or logs it.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF),
                )
            }
        }

        Card(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (hasKey) {
                    val key = secrets.geminiKey()
                    Text(
                        if (key != null) "Key saved: ${SecretStore.masked(key)}" else "A key is saved but could not be read; add it again.",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { secrets.clearGeminiKey() }) { Text("Remove key") }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Replace it", style = MaterialTheme.typography.labelLarge)
                }
                Text("How to get a key (free, about a minute)", style = MaterialTheme.typography.titleMedium)
                KeyStep("1", "Open Google AI Studio on this tablet or any computer:")
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = { uri.openUri(AI_STUDIO_URL) }) { Text("Open Google AI Studio") }
                    Text("aistudio.google.com/app/apikey", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
                }
                KeyStep("2", "Sign in with your Google account and accept Google's terms if asked.")
                KeyStep("3", "Tap \"Create API key\". If it asks for a project, pick the default one or create one.")
                KeyStep("4", "Copy the key. It starts with AIza and is 39 characters long.")
                KeyStep("5", "Paste it below and tap Save key.")
                Text(
                    "Keep the key private, like a password. You can delete it any time in Google AI Studio, " +
                        "and remove it here.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xB3FFFFFF),
                )
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it.trim(); error = null },
                    label = { Text("Gemini API key (starts with AIza)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = error != null,
                    supportingText = { error?.let { Text(it) } },
                    modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (secrets.setGeminiKey(input)) {
                            input = ""
                        } else {
                            error = "That doesn't look like a Gemini key: it should start with AIza and be 39 characters."
                        }
                    },
                    enabled = input.isNotBlank(),
                ) { Text("Save key") }
            }
        }

        // YouTube, for search and video length without the server.
        Card(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("YouTube key (optional, for use without the server)", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Without the OpenFit server, searching for videos and reading a video's length use YouTube's API. " +
                        "Your Gemini key works for this too once \"YouTube Data API v3\" is turned on for its Google Cloud " +
                        "project; or add a separate YouTube key here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = { uri.openUri(YOUTUBE_API_URL) }) { Text("Turn on YouTube Data API v3") }
                if (hasYouTube) {
                    val key = secrets.youtubeKey()
                    Text(if (key != null) "Key saved: ${SecretStore.masked(key)}" else "A key is saved but could not be read; add it again.")
                    OutlinedButton(onClick = { secrets.clearYouTubeKey() }) { Text("Remove YouTube key") }
                }
                OutlinedTextField(
                    value = youtubeInput,
                    onValueChange = { youtubeInput = it.trim(); youtubeError = null },
                    label = { Text("YouTube API key (starts with AIza)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    isError = youtubeError != null,
                    supportingText = { youtubeError?.let { Text(it) } },
                    modifier = Modifier.widthIn(max = 700.dp).fillMaxWidth(),
                )
                Button(
                    onClick = {
                        if (secrets.setYouTubeKey(youtubeInput)) youtubeInput = ""
                        else youtubeError = "That doesn't look like a Google API key: it should start with AIza and be 39 characters."
                    },
                    enabled = youtubeInput.isNotBlank(),
                ) { Text("Save YouTube key") }
            }
        }
    }
}

private const val YOUTUBE_API_URL = "https://console.cloud.google.com/apis/library/youtube.googleapis.com"

private const val AI_STUDIO_URL = "https://aistudio.google.com/app/apikey"

@Composable
private fun KeyStep(number: String, text: String) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            number,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF111111),
            modifier = Modifier
                .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                .padding(horizontal = 10.dp, vertical = 3.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyLarge)
    }
}

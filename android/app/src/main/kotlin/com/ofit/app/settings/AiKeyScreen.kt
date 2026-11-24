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
 * one, so it is free for them and has no daily limit from OpenFit (spec 009).
 */
@Composable
fun AiKeyScreen(secrets: SecretStore, onBack: () -> Unit) {
    val hasKey by secrets.hasGeminiKey.collectAsStateWithLifecycle()
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuButton()
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("Your Gemini key", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Card(Modifier.widthIn(max = 1000.dp).fillMaxWidth()) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Turn videos into workouts on your own Google account", style = MaterialTheme.typography.titleMedium)
                Text(
                    "OpenFit uses Google's Gemini to watch a YouTube video and work out the speed and incline. " +
                        "With your own key, that runs on your account: Google's free tier covers normal use, and " +
                        "OpenFit's shared daily limit no longer applies to you.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "Your key stays encrypted on this tablet and is sent only with video-to-workout requests. " +
                        "The server uses it for that request and never stores or logs it.",
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
                Text("1. Get a free key from Google AI Studio (sign in with your Google account, then \"Create API key\").")
                TextButton(onClick = { uri.openUri("https://aistudio.google.com/app/apikey") }) {
                    Text("Open Google AI Studio")
                }
                Text("2. Paste it here:")
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
    }
}

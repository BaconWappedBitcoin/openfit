package com.ofit.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The legal text, kept in step with DISCLAIMER.md in the repository. */
object Legal {
    /** Bump when the text changes, so everyone sees and accepts it again. */
    const val VERSION = 2

    val sections: List<Pair<String, String>> = listOf(
        "You use it at your own risk" to
            "OpenFit is free software that sends commands to exercise equipment that can move fast, change " +
            "incline and cause serious injury. It is provided \"as is\", without warranty of any kind. To the " +
            "fullest extent the law allows, its authors and contributors are not liable for any injury, death, " +
            "damage or loss arising from its use (GNU GPL v3, sections 15 and 16).",
        "Equipment safety" to
            "Always attach the treadmill's safety key to your clothing and know where the console's STOP button " +
            "is: software, Bluetooth and tablets can fail. Start slowly, never step onto a moving belt, keep the " +
            "area behind the treadmill clear, keep children and pets away, and never leave it running unattended.",
        "Health" to
            "OpenFit is not a medical device. Heart rate, zones, calories and distance are estimates. Talk to a " +
            "doctor before starting an exercise programme. Stop at once if you feel pain, faintness, dizziness or " +
            "shortness of breath. Hold zone depends on a sensor that can drop out: watch yourself, not only the screen.",
        "Your equipment" to
            "Controlling your equipment with third-party software may affect its warranty. You are responsible " +
            "for whether you may use OpenFit with it.",
        "Not affiliated" to
            "OpenFit is independent and is not made, endorsed or supported by iFit, NordicTrack, ProForm or any " +
            "manufacturer. Their names are trademarks of their owners. OpenFit contains no iFit code or content.",
        "Trademarks" to
            "iFIT, NordicTrack and ProForm are trademarks of their owner. Bluetooth is a registered trademark of " +
            "Bluetooth SIG, Inc. YouTube and Android are trademarks of Google LLC. Other names are trademarks of their " +
            "respective owners. They are used only to say what OpenFit is compatible with; no endorsement is implied.",
        "Third-party content" to
            "Videos play through YouTube's own player under YouTube's terms. Community workouts come from other " +
            "users and are not checked; their speeds and inclines may not suit you.",
    )
}

/** Shown before anything else until accepted (per tablet, per text version). */
@Composable
fun DisclaimerGate(onAccept: () -> Unit) {
    var agreed by remember { mutableStateOf(false) }
    // A Surface, so text takes the theme's light-on-dark colour (it was black on dark without one).
    androidx.compose.material3.Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
    Box(
        Modifier.fillMaxSize().statusBarsPadding(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 900.dp)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("OpenFit", color = MaterialTheme.colorScheme.primary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Text("Before you start", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(16.dp))
            LegalSections()
            Spacer(Modifier.height(16.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x14FFFFFF))
                    .padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = agreed, onCheckedChange = { agreed = it })
                Text("I have read this, I accept the risk, and I will use the safety key.")
            }
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAccept, enabled = agreed) { Text("Agree and continue") }
        }
    }
    }
}

/** Menu → About & legal. */
@Composable
fun LegalScreen(versionName: String, onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MenuButton()
            OutlinedButton(onClick = onBack) { Text("Back") }
            Text("About & legal", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("OpenFit $versionName · free software under the GNU GPL v3", style = MaterialTheme.typography.titleMedium)
        Text(
            "Source, licence and the full disclaimer: github.com/BaconWappedBitcoin/openfit",
            style = MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        Column(Modifier.widthIn(max = 1000.dp)) { LegalSections() }
        Spacer(Modifier.height(16.dp))
        Text(
            "Credits: protocol knowledge from public projects including DavisGoglin/treadmill, ianchi/ifit, " +
                "mikepugh/NordicFTMS and cagnulein's QZ; layout ideas from NuvioMobile.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun LegalSections() {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Legal.sections.forEach { (title, body) ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0x0FFFFFFF))
                    .padding(16.dp),
            ) {
                Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

package com.ofit.app.ui

import androidx.compose.material.icons.automirrored.filled.DirectionsRun

import androidx.compose.material.icons.filled.Code

import androidx.compose.foundation.layout.width

import androidx.compose.foundation.layout.size

import androidx.compose.foundation.clickable

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
        com.ofit.app.ui.ScreenHeader("About & legal", "Version, licence and safety", onBack = onBack)
        Spacer(Modifier.height(12.dp))
        AboutCard(versionName)
        DonateCard()
        Spacer(Modifier.height(16.dp))
        Column(Modifier.widthIn(max = 1000.dp)) { LegalSections() }
        Spacer(Modifier.height(16.dp))
        Text("No ads", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text(
            "OpenFit never shows ads. YouTube's own player may show YouTube's ads on some videos; OpenFit can't " +
                "remove those under YouTube's terms, but they can never change your workout: while the video is stopped " +
                "for an ad, the workout holds at its current speed and incline. For no ads at all, use a video file on the tablet or no video.",
            style = MaterialTheme.typography.bodyMedium,
        )
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

/** Where OpenFit's source lives. */
const val GITHUB_URL = "https://github.com/BaconWappedBitcoin/openfit"

/** Donation addresses (user request 2026-11-24); also in README.md. */
const val DONATE_BITCOIN = "bc1qh82ncrxc0pjszjugmk29gpyswnd0cfsklwr800"
const val DONATE_ETHEREUM = "0x16979A3283Ff840C2dD12Ea30d0e40a9508f81DA"

/** Support OpenFit: the Bitcoin and Ethereum addresses, each to copy or to open in a wallet app. */
@Composable
private fun DonateCard() {
    val context = androidx.compose.ui.platform.LocalContext.current
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    Column(
        Modifier
            .widthIn(max = 1000.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1A1A1A))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Support OpenFit", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            "OpenFit is free and has no ads. If it helps your training, a donation keeps it going.",
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xCCFFFFFF),
        )
        listOf(
            Triple("Bitcoin", DONATE_BITCOIN, "bitcoin:$DONATE_BITCOIN"),
            Triple("Ethereum", DONATE_ETHEREUM, "ethereum:$DONATE_ETHEREUM"),
        ).forEach { (coin, address, uri) ->
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(coin, fontWeight = FontWeight.SemiBold)
                    Text(
                        address,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xCCFFFFFF),
                    )
                }
                androidx.compose.material3.OutlinedButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(address))
                    android.widget.Toast.makeText(context, "$coin address copied", android.widget.Toast.LENGTH_SHORT).show()
                }) { Text("Copy") }
                androidx.compose.material3.OutlinedButton(onClick = {
                    val opened = runCatching {
                        context.startActivity(
                            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(uri))
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }.isSuccess
                    if (!opened) android.widget.Toast.makeText(context, "No $coin wallet app found; copy the address instead.", android.widget.Toast.LENGTH_LONG).show()
                }) { Text("Open wallet") }
            }
        }
    }
}

/** About: what OpenFit is, its version and licence, and links to the source on GitHub. */
@Composable
private fun AboutCard(versionName: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val accent = MaterialTheme.colorScheme.primary
    fun open(url: String) {
        runCatching {
            context.startActivity(
                android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                    .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
    androidx.compose.foundation.layout.Row(
        Modifier
            .widthIn(max = 1000.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.35f), Color(0xFF1A1A1A)),
                ),
            )
            .padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(accent),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.material3.Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Filled.DirectionsRun,
                contentDescription = null,
                tint = Color(0xFF0D0D0D),
                modifier = Modifier.size(56.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("OpenFit", fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Text(
                "Open treadmill control: run your treadmill from your own workouts and videos. " +
                    "Version $versionName · free software under the GNU GPL v3.",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xCCFFFFFF),
            )
            Text(GITHUB_URL.removePrefix("https://"), style = MaterialTheme.typography.bodyMedium, color = accent)
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Color(0xFFF5F7F8))
                        .clickable { open(GITHUB_URL) }
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Icon(
                        androidx.compose.material.icons.Icons.Filled.Code,
                        contentDescription = null,
                        tint = Color(0xFF0D0D0D),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("View on GitHub", color = Color(0xFF0D0D0D), fontWeight = FontWeight.Bold)
                }
                androidx.compose.material3.OutlinedButton(
                    onClick = { open("$GITHUB_URL/issues") },
                    modifier = Modifier.height(52.dp),
                ) { Text("Report a problem") }
            }
        }
    }
}

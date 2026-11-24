package com.ofit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** What Health Connect shows when someone asks why OpenFit wants access (required by Health Connect). */
class HealthRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFF111114)).padding(48.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text("OpenFit and Health Connect", color = Color.White, fontSize = 32.sp)
                    Text(
                        "When you turn it on, OpenFit writes each treadmill workout you finish to Health Connect: " +
                            "when it started and ended, walking or running, distance, calories, climb, heart rate and speed. " +
                            "OpenFit only writes; it does not read anything from Health Connect, and nothing is sent anywhere else. " +
                            "Turn it off at any time in OpenFit's menu, or remove access in Health Connect.",
                        color = Color(0xCCFFFFFF),
                        fontSize = 18.sp,
                    )
                    Button(onClick = { finish() }) { Text("Close") }
                }
            }
        }
    }
}

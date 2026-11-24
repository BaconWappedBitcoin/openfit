package com.ofit.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ofit.app.ui.OfitApp
import com.ofit.app.ui.theme.OfitTheme
import com.ofit.app.workout.AppScreen

/**
 * The single activity.
 *
 * The connection lives in the application object, not here, so that rotating the screen or
 * switching applications does not drop the treadmill.
 */
class MainActivity : ComponentActivity() {

    /** The controller, which lives for the life of the process. */
    private val controller: TreadmillController
        get() = (application as TreadmillApplication).controller

    /**
     * The Bluetooth permissions the app needs.
     *
     * On Android 12 and above, scanning and connecting are separate permissions. Below that, the
     * older location permission covers discovery.
     */
    private val permissions: Array<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )

        else -> arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        val missing = permissions.filterNot { granted[it] == true }
        if (missing.isNotEmpty()) {
            controller.showMessage("The app needs permission to use Bluetooth: ${missing.joinToString()}.")
        } else {
            controller.connectStrap()
        }
    }

    /** Where the application is. */
    private var screen by mutableStateOf<AppScreen>(AppScreen.Home)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Go straight back to the treadmill used last time, with no scan or tap.
        if (hasBluetoothPermissions()) controller.connectStrap()

        // Several people share the tablet: ask who is running first.
        if (savedInstanceState == null && controller.profiles.shouldAsk) screen = AppScreen.Profiles

        setContent {
            val profile by controller.profiles.current.collectAsState()
            val fullscreen by controller.settings.fullscreen.collectAsState()
            // Re-applied on every screen change: the workout screen always hides the bars, and a
            // dialog or the keyboard can bring them back.
            androidx.compose.runtime.LaunchedEffect(fullscreen, screen) {
                applyFullscreen(fullscreen || screen is AppScreen.Run)
            }
            OfitTheme(accent = profile.accent) {
                OfitApp(
                    controller = controller,
                    screen = screen,
                    onNavigate = { destination -> screen = destination },
                    onScan = {
                        if (hasBluetoothPermissions()) {
                            controller.startScan()
                        } else {
                            permissionLauncher.launch(permissions)
                        }
                    },
                )
            }
        }
    }

    /**
     * Fullscreen: hide the status bar and the tablet's taskbar / navigation bar. A swipe in from the
     * edge shows them for a moment, then they hide again.
     */
    private fun applyFullscreen(on: Boolean) {
        val bars = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        if (on) {
            bars.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            bars.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else {
            bars.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Coming back from another app or a system dialog shows the bars again; hide them.
        if (hasFocus && (controller.settings.fullscreen.value || screen is AppScreen.Run)) {
            applyFullscreen(true)
        }
    }

    /** Whether every Bluetooth permission has been granted. */
    private fun hasBluetoothPermissions(): Boolean = permissions.all { permission ->
        checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED
    }
}

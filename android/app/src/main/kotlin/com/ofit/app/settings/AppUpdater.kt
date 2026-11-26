package com.ofit.app.settings

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import com.ofit.protocol.AppUpdate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Updates from the project's GitHub releases (user request 2026-11-24, as Nuvio does). It looks at
 * the latest release, downloads its APK, checks it against the SHA-256 in the release notes, and
 * hands it to Android's package installer. Nothing installs by itself (user request 2026-11-25):
 * the runner taps Update, and Android's own Install confirmation always follows. Never during a
 * workout: [canInstallNow] is checked before the download, after it, and again before Android asks.
 */
class AppUpdater(
    context: Context,
    private val scope: CoroutineScope,
    /** False while a workout runs: installing restarts the app. */
    private val canInstallNow: () -> Boolean,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("ofit_updates", Context.MODE_PRIVATE)

    data class Release(val version: String, val apkUrl: String, val apkName: String, val sha256: String?, val notes: String)

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release) : State
        data class Installing(val release: Release) : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** The version the runner chose "Later" for, so Home doesn't ask again until a newer one. */
    fun laterFor(version: String) = prefs.edit().putString(KEY_LATER, version).apply()

    fun wantsAsking(release: Release): Boolean = prefs.getString(KEY_LATER, null) != release.version

    val currentVersion: String = runCatching { app.packageManager.getPackageInfo(app.packageName, 0).versionName }.getOrNull() ?: "0"

    /** A development build is signed with a different key, so a release can't replace it. */
    val isDevelopmentBuild: Boolean = app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    /** At start: look for an update at most once every 6 hours. It only checks: installing waits for the runner. */
    fun checkAtStart() {
        val last = prefs.getLong(KEY_LAST, 0)
        if (System.currentTimeMillis() - last < 6 * 3600_000L) return
        scope.launch { check() }
    }

    /** Look for a newer release; returns it, or null when up to date or the check failed. */
    suspend fun check(): Release? {
        _state.value = State.Checking
        return runCatching { fetchLatest() }.fold(
            onSuccess = { release ->
                prefs.edit().putLong(KEY_LAST, System.currentTimeMillis()).apply()
                if (release != null && AppUpdate.isNewer(release.version, currentVersion)) {
                    _state.value = State.Available(release)
                    release
                } else {
                    _state.value = State.UpToDate
                    null
                }
            },
            onFailure = {
                Log.w(TAG, "update check failed", it)
                _state.value = State.Failed("Could not check for updates: ${it.message ?: "no connection"}")
                null
            },
        )
    }

    /** Download, check and install [release]. Nothing happens during a workout. */
    fun install(release: Release) {
        scope.launch {
            if (!canInstallNow()) {
                _state.value = State.Available(release)
                return@launch
            }
            _state.value = State.Downloading(release)
            val file = runCatching { download(release) }.getOrElse {
                _state.value = State.Failed("Download failed: ${it.message}")
                return@launch
            }
            val sha = withContext(Dispatchers.IO) { sha256(file) }
            if (release.sha256 == null || sha != release.sha256) {
                file.delete()
                _state.value = State.Failed("The download did not match the release's checksum, so it was not installed.")
                return@launch
            }
            if (!canInstallNow()) {
                _state.value = State.Available(release)
                return@launch
            }
            _state.value = State.Installing(release)
            runCatching { startInstall(file) }.onFailure {
                _state.value = State.Failed("Could not install: ${it.message}")
            }
        }
    }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val c = URL(LATEST).openConnection() as HttpURLConnection
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "OpenFit-app")
        c.connectTimeout = 15_000
        c.readTimeout = 15_000
        try {
            if (c.responseCode != 200) throw IllegalStateException("GitHub answered HTTP ${c.responseCode}")
            val json = JSONObject(c.inputStream.bufferedReader().readText())
            val assets = json.optJSONArray("assets")
            val names = (0 until (assets?.length() ?: 0)).map { assets!!.getJSONObject(it).optString("name") }
            val apk = AppUpdate.apkName(names) ?: return@withContext null
            val url = assets!!.getJSONObject(names.indexOf(apk)).optString("browser_download_url")
            val body = json.optString("body")
            Release(json.optString("tag_name").removePrefix("v"), url, apk, AppUpdate.sha256From(body), body)
        } finally {
            c.disconnect()
        }
    }

    private suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val out = File(dir, release.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        var url = URL(release.apkUrl)
        // GitHub sends the file from another address: follow up to 5 redirects.
        repeat(5) {
            val c = url.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.setRequestProperty("User-Agent", "OpenFit-app")
            c.connectTimeout = 15_000
            c.readTimeout = 60_000
            when (c.responseCode) {
                in 300..399 -> {
                    url = URL(c.getHeaderField("Location"))
                    c.disconnect()
                }
                200 -> {
                    c.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
                    c.disconnect()
                    return@withContext out
                }
                else -> throw IllegalStateException("HTTP ${c.responseCode}")
            }
        }
        throw IllegalStateException("too many redirects")
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buf)
                if (n < 0) break
                md.update(buf, 0, n)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun startInstall(file: File) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            // Android always asks the runner to confirm: never a silent install.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id = installer.createSession(params)
        installer.openSession(id).use { session ->
            session.openWrite("openfit.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(app, UpdateResultReceiver::class.java)
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
            session.commit(PendingIntent.getBroadcast(app, id, intent, flags).intentSender)
        }
    }

    companion object {
        private const val TAG = "OfitUpdate"
        private const val KEY_LATER = "later"
        private const val KEY_LAST = "last_check"
        const val LATEST = "https://api.github.com/repos/BaconWappedBitcoin/openfit/releases/latest"
    }
}

/** What Android says about an update install: it may need the runner's OK, or it failed. */
class UpdateResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // A workout started meanwhile: no Install screen over it; the update is called off.
                val controller = (context.applicationContext as? com.ofit.app.TreadmillApplication)?.controller
                if (controller?.workouts?.snapshot?.value?.isActive == true) {
                    val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
                    if (sessionId >= 0) runCatching { context.packageManager.packageInstaller.abandonSession(sessionId) }
                    Log.i("OfitUpdate", "update called off: a workout is running")
                    return
                }
                // Android shows its own Install screen, and the runner decides.
                @Suppress("DEPRECATION")
                val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                runCatching { context.startActivity(confirm) }
            }
            PackageInstaller.STATUS_SUCCESS -> Log.i("OfitUpdate", "update installed")
            else -> Log.w("OfitUpdate", "update install failed ($status): ${intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)}")
        }
    }
}

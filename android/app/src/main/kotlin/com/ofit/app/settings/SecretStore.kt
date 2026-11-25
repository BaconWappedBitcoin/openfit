package com.ofit.app.settings

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's own Gemini API key ("bring your own key"), kept on this tablet only.
 *
 * Encrypted with an AES-GCM key held in the Android Keystore (hardware-backed where the device has
 * it), so the stored text is useless if copied off the device. It is sent only with video-to-workout
 * requests, in a header the backend uses and never stores or logs. Shared by every profile.
 */
class SecretStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _hasGeminiKey = MutableStateFlow(prefs.contains(KEY_GEMINI))
    val hasGeminiKey: StateFlow<Boolean> = _hasGeminiKey.asStateFlow()

    private val _hasYouTubeKey = MutableStateFlow(prefs.contains(KEY_YOUTUBE))
    val hasYouTubeKey: StateFlow<Boolean> = _hasYouTubeKey.asStateFlow()

    /**
     * Whether to use the hosted OpenFit server (on by default). Off, OpenFit needs no server at all:
     * the tablet uses the keys here and reads the community library from the project's repository.
     */
    private val _useServer = MutableStateFlow(prefs.getBoolean(KEY_USE_SERVER, true))
    val useServer: StateFlow<Boolean> = _useServer.asStateFlow()

    fun setUseServer(on: Boolean) {
        prefs.edit().putBoolean(KEY_USE_SERVER, on).apply()
        _useServer.value = on
    }

    /** Which OpenFit server: the hosted one, or a self-hoster's own deployment of backend/. */
    private val _serverUrl = MutableStateFlow(prefs.getString(KEY_SERVER_URL, null) ?: com.ofit.protocol.ServerUrl.DEFAULT)
    val serverUrl: StateFlow<String> = _serverUrl.asStateFlow()

    /** Sets the server address; false if it cannot be used. Null goes back to the hosted server. */
    fun setServerUrl(input: String?): Boolean {
        val url = if (input == null) com.ofit.protocol.ServerUrl.DEFAULT else com.ofit.protocol.ServerUrl.normalize(input) ?: return false
        if (url == com.ofit.protocol.ServerUrl.DEFAULT) prefs.edit().remove(KEY_SERVER_URL).apply() else prefs.edit().putString(KEY_SERVER_URL, url).apply()
        _serverUrl.value = url
        return true
    }

    /** The user's own YouTube Data API key, or null. */
    fun youtubeKey(): String? = open(KEY_YOUTUBE)

    fun setYouTubeKey(key: String): Boolean {
        val clean = key.trim()
        if (!looksLikeGeminiKey(clean)) return false
        seal(KEY_YOUTUBE, clean)
        _hasYouTubeKey.value = true
        return true
    }

    fun clearYouTubeKey() {
        prefs.edit().remove(KEY_YOUTUBE).apply()
        _hasYouTubeKey.value = false
    }

    // The project owner's token (user request 2026-11-24): with it, community workouts can be
    // removed from the library at once. Kept encrypted like the keys; nobody else needs one.
    private val _hasOwnerToken = MutableStateFlow(prefs.contains(KEY_OWNER))
    val hasOwnerToken: StateFlow<Boolean> = _hasOwnerToken.asStateFlow()

    fun ownerToken(): String? = open(KEY_OWNER)

    fun setOwnerToken(token: String): Boolean {
        val clean = token.trim()
        if (clean.length < 24 || clean.any { it.isWhitespace() }) return false
        seal(KEY_OWNER, clean)
        _hasOwnerToken.value = true
        return true
    }

    fun clearOwnerToken() {
        prefs.edit().remove(KEY_OWNER).apply()
        _hasOwnerToken.value = false
    }

    private fun open(name: String): String? {
        val stored = prefs.getString(name, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun seal(name: String, value: String) {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(name, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
    }

    /** The key, or null if none is saved or it cannot be decrypted (e.g. the keystore was reset). */
    fun geminiKey(): String? {
        val stored = prefs.getString(KEY_GEMINI, null) ?: return null
        return runCatching {
            val bytes = Base64.decode(stored, Base64.NO_WRAP)
            val iv = bytes.copyOfRange(0, IV_BYTES)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
        }.getOrNull()
    }

    /** Save a key; returns false when it does not look like a Google API key. */
    fun setGeminiKey(key: String): Boolean {
        val clean = key.trim()
        if (!looksLikeGeminiKey(clean)) return false
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val sealed = cipher.iv + cipher.doFinal(clean.toByteArray(Charsets.UTF_8))
        prefs.edit().putString(KEY_GEMINI, Base64.encodeToString(sealed, Base64.NO_WRAP)).apply()
        _hasGeminiKey.value = true
        return true
    }

    fun clearGeminiKey() {
        prefs.edit().remove(KEY_GEMINI).apply()
        _hasGeminiKey.value = false
    }

    private fun secretKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        private const val PREFS = "ofit_secrets"
        private const val KEY_GEMINI = "gemini_key"
        private const val KEY_YOUTUBE = "youtube_key"
        private const val KEY_OWNER = "owner_token"
        private const val KEY_USE_SERVER = "use_server"
        private const val KEY_SERVER_URL = "server_url"
        private const val ALIAS = "openfit_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12

        /** Google API keys: "AIza" followed by 35 URL-safe characters (same rule as the backend). */
        private val KEY_SHAPE = Regex("^AIza[0-9A-Za-z_-]{35}$")

        /** Google's newer keys (AI Studio from 2026): "AQ." and then letters, digits, "_", "-" and ".". */
        private val AUTH_KEY_SHAPE = Regex("^AQ[.][0-9A-Za-z_.-]{20,300}$")

        /** A Google API key in either form: the older "AIza…" or the newer "AQ.…" (user report 2026-11-24). */
        fun looksLikeGeminiKey(key: String): Boolean = key.trim().let { KEY_SHAPE.matches(it) || AUTH_KEY_SHAPE.matches(it) }

        /** For display: the first and last four characters only. */
        fun masked(key: String): String = if (key.length < 12) "••••" else key.take(4) + "••••••••" + key.takeLast(4)
    }
}

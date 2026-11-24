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
        private const val ALIAS = "openfit_secrets"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12

        /** Google API keys: "AIza" followed by 35 URL-safe characters (same rule as the backend). */
        private val KEY_SHAPE = Regex("^AIza[0-9A-Za-z_-]{35}$")

        fun looksLikeGeminiKey(key: String): Boolean = KEY_SHAPE.matches(key.trim())

        /** For display: the first and last four characters only. */
        fun masked(key: String): String = if (key.length < 12) "••••" else key.take(4) + "••••••••" + key.takeLast(4)
    }
}

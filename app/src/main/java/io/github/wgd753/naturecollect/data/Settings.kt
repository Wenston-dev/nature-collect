package io.github.wgd753.naturecollect.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.github.wgd753.naturecollect.ui.Lang
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * App settings. The DeepSeek API key is encrypted with an AES key held in the
 * Android Keystore, so it never sits in plain text on disk.
 */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _apiKey = MutableStateFlow(readApiKey())
    val apiKey: StateFlow<String?> = _apiKey.asStateFlow()

    private val _autoRecognize = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    val autoRecognize: StateFlow<Boolean> = _autoRecognize.asStateFlow()

    /** Base map: "amap" (高德 tiles, fast in mainland China) or "osm" (OpenFreeMap). */
    private val _mapSource = MutableStateFlow(prefs.getString(KEY_MAP, MAP_AMAP) ?: MAP_AMAP)
    val mapSource: StateFlow<String> = _mapSource.asStateFlow()

    fun setMapSource(value: String) {
        prefs.edit().putString(KEY_MAP, value).apply()
        _mapSource.value = value
    }

    private val _language = MutableStateFlow(prefs.getString(KEY_LANG, LANG_ZH) ?: LANG_ZH)
    val language: StateFlow<String> = _language.asStateFlow()

    init {
        Lang.english = _language.value == LANG_EN
    }

    fun setLanguage(value: String) {
        prefs.edit().putString(KEY_LANG, value).apply()
        _language.value = value
        Lang.english = value == LANG_EN
    }

    private val _onboarded = MutableStateFlow(prefs.getBoolean(KEY_ONBOARDED, false))
    val onboarded: StateFlow<Boolean> = _onboarded.asStateFlow()

    fun setOnboarded() {
        prefs.edit().putBoolean(KEY_ONBOARDED, true).apply()
        _onboarded.value = true
    }

    fun setApiKey(value: String?) {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) {
            prefs.edit().remove(KEY_API).apply()
            _apiKey.value = null
        } else {
            prefs.edit().putString(KEY_API, encrypt(trimmed)).apply()
            _apiKey.value = trimmed
        }
    }

    fun setAutoRecognize(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
        _autoRecognize.value = enabled
    }

    private fun readApiKey(): String? =
        prefs.getString(KEY_API, null)?.let { runCatching { decrypt(it) }.getOrNull() }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return generator.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = Base64.encodeToString(cipher.iv, Base64.NO_WRAP)
        val data = Base64.encodeToString(cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        return "$iv:$data"
    }

    private fun decrypt(stored: String): String {
        val (iv, data) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
        return String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)))
    }

    companion object {
        const val MAP_AMAP = "amap"
        const val MAP_OSM = "osm"
        const val LANG_ZH = "zh"
        const val LANG_EN = "en"
        private const val KEY_LANG = "language"
        private const val KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "deepseek_api_key"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_API = "api_key"
        private const val KEY_AUTO = "auto_recognize"
        private const val KEY_MAP = "map_source"
        private const val KEY_ONBOARDED = "onboarded"
    }
}

package com.jondude1.wisp

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** User settings. The API key lives in Keystore-backed encrypted storage. */
class Settings(ctx: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        ctx,
        "wisp_secure",
        MasterKey.Builder(ctx).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    var apiKey by mutableStateOf(prefs.getString("api_key", "") ?: "")
        private set
    var model by mutableStateOf(prefs.getString("model", DEFAULT_MODEL) ?: DEFAULT_MODEL)
        private set
    var speakReplies by mutableStateOf(prefs.getBoolean("speak", true))
        private set
    var webSearch by mutableStateOf(prefs.getBoolean("web_search", false))
        private set

    var immersive by mutableStateOf(prefs.getBoolean("immersive", true))
        private set

    fun updateImmersive(v: Boolean) { immersive = v; prefs.edit().putBoolean("immersive", v).apply() }
    fun updateApiKey(v: String) { apiKey = v.trim(); prefs.edit().putString("api_key", apiKey).apply() }
    fun updateModel(v: String) { model = v.trim(); prefs.edit().putString("model", model).apply() }
    fun updateSpeak(v: Boolean) { speakReplies = v; prefs.edit().putBoolean("speak", v).apply() }
    fun updateWebSearch(v: Boolean) { webSearch = v; prefs.edit().putBoolean("web_search", v).apply() }

    companion object {
        const val DEFAULT_MODEL = "claude-sonnet-5-5"
    }
}

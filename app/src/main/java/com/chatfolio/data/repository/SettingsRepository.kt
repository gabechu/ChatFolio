package com.chatfolio.data.repository

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPrefs = EncryptedSharedPreferences.create(
        context,
        "chatfolio_secure_settings",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    companion object {
        private const val KEY_GEMINI_MODEL_NAME = "gemini_model_name"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key_secure"
    }

    fun getGeminiApiKey(): String? {
        return sharedPrefs.getString(KEY_GEMINI_API_KEY, null)
    }

    fun saveGeminiApiKey(apiKey: String) {
        sharedPrefs.edit().putString(KEY_GEMINI_API_KEY, apiKey).apply()
    }

    fun getGeminiModelName(): String {
        return sharedPrefs.getString(KEY_GEMINI_MODEL_NAME, null) ?: "gemini-2.5-flash"
    }

    fun saveGeminiModelName(modelName: String) {
        sharedPrefs.edit().putString(KEY_GEMINI_MODEL_NAME, modelName).apply()
    }
}

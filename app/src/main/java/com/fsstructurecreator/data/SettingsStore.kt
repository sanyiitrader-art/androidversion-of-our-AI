package com.fsstructurecreator.data

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

// API key persistence (spec section 15). Uses Android's
// EncryptedSharedPreferences -- the practical secure-storage
// equivalent of the Windows settings.json, but encrypted at rest,
// which is a reasonable "appropriate Android secure storage practice"
// per the spec since this file never touches a plaintext file on disk.
//
// Saving a new key immediately overwrites the old one -- no key
// history, no switching, matching section 15 exactly (this differs
// slightly from the Windows behavior, which is intentional per spec).

class SettingsStore(context: Context) {

    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs = EncryptedSharedPreferences.create(
        context,
        PREFS_FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    /** Overwrites any existing key. The key is never logged. */
    fun setApiKey(key: String) {
        prefs.edit().putString(KEY_GEMINI_API_KEY, key).apply()
    }

    /** Returns whether a key is currently saved, without exposing its
     *  value -- used only to decide UI state (e.g. the "key already
     *  saved" placeholder in ApiKeyDialog.kt), never to render the
     *  key itself. */
    fun hasApiKey(): Boolean {
        return !prefs.getString(KEY_GEMINI_API_KEY, null).isNullOrEmpty()
    }

    /** Returns the actual key value. Called only by GeminiClient.kt
     *  immediately before making a request, and never used to
     *  populate any visible UI field. */
    fun getApiKey(): String? {
        return prefs.getString(KEY_GEMINI_API_KEY, null)
    }

    companion object {
        private const val PREFS_FILE_NAME = "fs_structure_creator_settings"
        private const val KEY_GEMINI_API_KEY = "gemini_api_key"
    }
}
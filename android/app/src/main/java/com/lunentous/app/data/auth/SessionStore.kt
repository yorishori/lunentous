package com.lunentous.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Keystore-backed storage for the two things needed to talk to a
 * self-hosted Lunentous server: its base URL and a bearer API key.
 *
 * Unlike the web app (served from the same origin as its API, so it only
 * ever needs the key), this is a standalone client -- there's no "same
 * origin" to infer the server from, so the URL has to be configured too.
 */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences = run {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "lunentous_secure_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getBaseUrl(): String? = prefs.getString(KEY_BASE_URL, null)
    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun hasSession(): Boolean = getBaseUrl() != null && getToken() != null

    fun saveSession(baseUrl: String, token: String) {
        prefs.edit()
            .putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl))
            .putString(KEY_TOKEN, token)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_BASE_URL = "server_base_url"
        private const val KEY_TOKEN = "api_token"

        /** Ensures a trailing slash (Retrofit's baseUrl requires one) and a
         * scheme, so users can type "192.168.1.50:8080" without thinking
         * about it. */
        fun normalizeBaseUrl(input: String): String {
            var url = input.trim()
            if (!url.startsWith("http://") && !url.startsWith("https://")) {
                url = "http://$url"
            }
            if (!url.endsWith("/")) {
                url = "$url/"
            }
            return url
        }
    }
}

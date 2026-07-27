package com.lunentous.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

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

    /** Set by OutboxProcessor on a 401 -- the stored key was rejected by
     * the server (revoked, wrong server, etc). Settings surfaces this as a
     * "reconnect" prompt; saveSession()/clear() both reset it. */
    private val reauthRequiredFlow = MutableStateFlow(false)
    val reauthRequired: StateFlow<Boolean> = reauthRequiredFlow

    fun markReauthRequired() {
        reauthRequiredFlow.value = true
    }

    fun saveSession(baseUrl: String, token: String) {
        prefs.edit()
            .putString(KEY_BASE_URL, normalizeBaseUrl(baseUrl))
            .putString(KEY_TOKEN, token)
            .apply()
        reauthRequiredFlow.value = false
    }

    fun clear() {
        prefs.edit().clear().apply()
        reauthRequiredFlow.value = false
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

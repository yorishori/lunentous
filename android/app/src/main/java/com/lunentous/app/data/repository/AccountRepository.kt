package com.lunentous.app.data.repository

import com.lunentous.app.data.auth.SessionStore
import com.lunentous.app.data.remote.LunentousApi
import com.lunentous.app.data.remote.dto.ApiKeyDto
import com.lunentous.app.data.remote.dto.CreateApiKeyRequest
import com.lunentous.app.data.remote.dto.CreatedApiKeyDto
import okhttp3.ResponseBody

/**
 * API keys and the full-database export are server-only concerns with no
 * local (Room) representation -- unlike everything else in the app, there's
 * nothing meaningful to do with them offline, so this repository is a thin
 * network-only wrapper rather than following the read-from-Room pattern.
 */
class AccountRepository(private val api: LunentousApi, private val sessionStore: SessionStore) {
    suspend fun getApiKeys(): Result<List<ApiKeyDto>> = runCatching {
        check(sessionStore.hasSession()) { "Not connected to a server" }
        api.getApiKeys()
    }

    suspend fun createApiKey(label: String?): Result<CreatedApiKeyDto> = runCatching {
        check(sessionStore.hasSession()) { "Not connected to a server" }
        api.createApiKey(CreateApiKeyRequest(label?.ifBlank { null }))
    }

    suspend fun revokeApiKey(id: Long): Result<Unit> = runCatching {
        check(sessionStore.hasSession()) { "Not connected to a server" }
        api.deleteApiKey(id)
        Unit
    }

    suspend fun exportBackup(): Result<ResponseBody> = runCatching {
        check(sessionStore.hasSession()) { "Not connected to a server" }
        api.export()
    }
}

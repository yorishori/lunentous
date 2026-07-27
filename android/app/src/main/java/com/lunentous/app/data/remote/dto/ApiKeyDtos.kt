package com.lunentous.app.data.remote.dto

import com.google.gson.annotations.SerializedName

/** Mirrors GET /api/api-keys -- never the hash or plaintext. */
data class ApiKeyDto(
    val id: Long,
    val label: String?,
    @SerializedName("created_at") val createdAt: String,
)

/** POST /api/api-keys returns the plaintext token once, only at creation. */
data class CreatedApiKeyDto(
    val id: Long,
    val label: String?,
    val token: String,
)

data class CreateApiKeyRequest(val label: String? = null)

package echo.music.desktop.auth

import kotlinx.serialization.Serializable

@Serializable data class HandshakeResponse(val token: String)

@Serializable
data class SyncRequest(
  val token: String = "",
  val cookies: Map<String, String> = emptyMap(),
  val userAgent: String? = null,
)

@Serializable
data class SyncStatusResponse(
  val synced: Boolean,
  val lastSyncEpochMs: Long? = null,
  val cookieNames: List<String> = emptyList(),
)

@Serializable data class SyncResultResponse(val status: String, val reason: String? = null)

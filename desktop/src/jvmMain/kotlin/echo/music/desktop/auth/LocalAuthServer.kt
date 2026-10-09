package echo.music.desktop.auth

import com.music.innertube.YouTube
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.plugins.origin
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import java.security.SecureRandom
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

internal const val LOOPBACK_HOST = "127.0.0.1"

internal val REQUIRED_AUTH_COOKIES =
  setOf("SAPISID", "SSID", "HSID", "LOGIN_INFO", "__Secure-3PSAPISID")

internal fun isLoopback(remoteHost: String?): Boolean {
  val host = remoteHost?.trim()?.lowercase() ?: return false
  if (host == "localhost" || host == "::1" || host == "[::1]" || host == "0:0:0:0:0:0:0:1") {
    return true
  }
  if (!host.startsWith("127.")) return false
  val parts = host.split(".")
  return parts.size == 4 &&
    parts.all { part ->
      part.isNotEmpty() && part.all(Char::isDigit) && part.toIntOrNull() in 0..255
    }
}

internal fun isAllowedExtensionOrigin(origin: String): Boolean =
  origin == "null" ||
    origin.startsWith("chrome-extension://") ||
    origin.startsWith("moz-extension://")

data class AuthSyncStatus(
  val synced: Boolean = false,
  val lastSyncEpochMs: Long? = null,
  val cookieNames: List<String> = emptyList(),
)

object AuthSyncState {
  private val _status = MutableStateFlow(AuthSyncStatus())
  val status: StateFlow<AuthSyncStatus> = _status.asStateFlow()

  fun recordSync(cookies: Map<String, String>, nowEpochMs: Long = System.currentTimeMillis()) {
    _status.value =
      AuthSyncStatus(
        synced = true,
        lastSyncEpochMs = nowEpochMs,
        cookieNames = cookies.keys.sorted(),
      )
  }

  fun reset() {
    _status.value = AuthSyncStatus()
  }
}

internal fun defaultCookieSink(cookies: Map<String, String>) {
  YouTube.cookie = cookies.entries.joinToString(separator = "; ") { "${it.key}=${it.value}" }
}

class LocalAuthServer(
  private val port: Int = DEFAULT_PORT,
  private val tokenTtlMillis: Long = DEFAULT_TOKEN_TTL_MILLIS,
) {
  internal var cookieSink: (Map<String, String>) -> Unit = ::defaultCookieSink

  private val secureRandom = SecureRandom()
  private val tokens = ConcurrentHashMap<String, Long>()
  private var server: EmbeddedServer<*, *>? = null

  val isRunning: Boolean
    get() = server != null

  var boundPort: Int = port
    private set

  fun start() {
    check(server == null) { "LocalAuthServer is already running" }
    val embedded =
      embeddedServer(CIO, port = port, host = LOOPBACK_HOST, module = ::configure)
        .start(wait = false)
    boundPort = runBlocking { embedded.engine.resolvedConnectors().first().port }
    server = embedded
  }

  fun stop() {
    server?.stop(gracePeriodMillis = 100, timeoutMillis = 1000)
    server = null
  }

  private fun issueToken(nowEpochMs: Long): String {
    val bytes = ByteArray(TOKEN_BYTES)
    secureRandom.nextBytes(bytes)
    val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    tokens[token] = nowEpochMs + tokenTtlMillis
    return token
  }

  private fun consumeToken(token: String, nowEpochMs: Long): Boolean {
    val expiry = tokens.remove(token) ?: return false
    return nowEpochMs < expiry
  }

  private fun configure(app: Application) {
    app.apply {
      install(ContentNegotiation) {
        json(
          Json {
            ignoreUnknownKeys = true
            explicitNulls = false
          }
        )
      }
      install(CORS) {
        allowOrigins(::isAllowedExtensionOrigin)
        allowMethod(HttpMethod.Get)
        allowMethod(HttpMethod.Post)
        allowHeader(HttpHeaders.ContentType)
      }
      routing {
        get("/auth/handshake") {
          if (!call.requireLoopback()) return@get
          call.respond(HandshakeResponse(issueToken(System.currentTimeMillis())))
        }
        post("/auth/sync") {
          if (!call.requireLoopback()) return@post
          val request =
            try {
              call.receive<SyncRequest>()
            } catch (e: Exception) {
              call.respond(HttpStatusCode.BadRequest, SyncResultResponse("error", "malformed_body"))
              return@post
            }
          if (!consumeToken(request.token, System.currentTimeMillis())) {
            call.respond(HttpStatusCode.Forbidden, SyncResultResponse("error", "invalid_token"))
            return@post
          }
          val cookies = request.cookies
          if (cookies.isEmpty() || cookies.any { it.key.isBlank() || it.value.isBlank() }) {
            call.respond(HttpStatusCode.BadRequest, SyncResultResponse("error", "invalid_cookies"))
            return@post
          }
          if (REQUIRED_AUTH_COOKIES.none { !cookies[it].isNullOrBlank() }) {
            call.respond(
              HttpStatusCode.BadRequest,
              SyncResultResponse("error", "missing_auth_cookies"),
            )
            return@post
          }
          cookieSink(cookies)
          AuthSyncState.recordSync(cookies)
          call.respond(SyncResultResponse("ok"))
        }
        get("/auth/status") {
          if (!call.requireLoopback()) return@get
          val current = AuthSyncState.status.value
          call.respond(
            SyncStatusResponse(current.synced, current.lastSyncEpochMs, current.cookieNames)
          )
        }
      }
    }
  }

  private suspend fun ApplicationCall.requireLoopback(): Boolean {
    val connectionPoint = request.origin
    if (isLoopback(connectionPoint.remoteHost) || isLoopback(connectionPoint.remoteAddress)) {
      return true
    }
    respond(HttpStatusCode.Forbidden, SyncResultResponse("error", "not_loopback"))
    return false
  }

  companion object {
    const val DEFAULT_PORT = 45454
    const val DEFAULT_TOKEN_TTL_MILLIS = 5 * 60 * 1000L
    private const val TOKEN_BYTES = 32
  }
}

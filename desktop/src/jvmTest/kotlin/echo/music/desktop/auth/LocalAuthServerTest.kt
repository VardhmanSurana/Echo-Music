package echo.music.desktop.auth

import com.music.innertube.YouTube
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO as ClientCIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation as ClientContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class LocalAuthServerTest {
  private lateinit var server: LocalAuthServer
  private lateinit var client: HttpClient
  private var sunkCookies: Map<String, String>? = null

  @BeforeTest
  fun setUp() {
    AuthSyncState.reset()
    sunkCookies = null
    server = LocalAuthServer(port = 0)
    server.setLegacyCookieSink { cookies -> sunkCookies = cookies }
    server.start()
    client =
      HttpClient(ClientCIO) {
        install(ClientContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
      }
  }

  @AfterTest
  fun tearDown() {
    client.close()
    server.stop()
    AuthSyncState.reset()
    YouTube.cookie = null
  }

  private fun handshake(port: Int = server.boundPort): HandshakeResponse = runBlocking {
    val response = client.get("http://$LOOPBACK_HOST:$port/auth/handshake")
    assertEquals(HttpStatusCode.OK, response.status)
    response.body()
  }

  private fun sync(
    token: String,
    cookies: Map<String, String>,
    port: Int = server.boundPort,
  ): HttpResponse = runBlocking {
    client.post("http://$LOOPBACK_HOST:$port/auth/sync") {
      contentType(ContentType.Application.Json)
      setBody(SyncRequest(token = token, cookies = cookies, userAgent = "test-agent"))
    }
  }

  @Test
  fun handshakeReturnsUsableToken() {
    assertTrue(handshake().token.isNotBlank())
  }

  @Test
  fun syncWithValidTokenSucceedsAndUpdatesState() = runBlocking {
    val cookies = authCookies()
    val response = sync(handshake().token, cookies)
    assertEquals(HttpStatusCode.OK, response.status)
    val body: SyncResultResponse = response.body()
    assertEquals("ok", body.status)
    assertEquals(cookies, sunkCookies)

    val status = AuthSyncState.status.value
    assertTrue(status.synced)
    assertNotNull(status.lastSyncEpochMs)
    assertEquals(listOf("HSID", "LOGIN_INFO", "SAPISID"), status.cookieNames)

    val statusResponse = client.get("http://$LOOPBACK_HOST:${server.boundPort}/auth/status")
    assertEquals(HttpStatusCode.OK, statusResponse.status)
    val statusBody: SyncStatusResponse = statusResponse.body()
    assertTrue(statusBody.synced)
    assertNotNull(statusBody.lastSyncEpochMs)
    assertEquals(listOf("HSID", "LOGIN_INFO", "SAPISID"), statusBody.cookieNames)
  }

  @Test
  fun defaultCookieSinkFeedsInnerTubeCookieHook() = runBlocking {
    val defaultServer = LocalAuthServer(port = 0)
    defaultServer.start()
    try {
      val response =
        sync(handshake(defaultServer.boundPort).token, authCookies(), defaultServer.boundPort)
      assertEquals(HttpStatusCode.OK, response.status)
      val cookie = assertNotNull(YouTube.cookie)
      assertTrue(cookie.contains("SAPISID=sapisid-value"))
      assertTrue(cookie.contains("HSID=hsid-value"))
      assertTrue(cookie.contains("; "))
    } finally {
      defaultServer.stop()
    }
  }

  @Test
  fun reusedTokenIsRejected() = runBlocking {
    val token = handshake().token
    assertEquals(HttpStatusCode.OK, sync(token, authCookies()).status)
    val reused = sync(token, authCookies())
    assertEquals(HttpStatusCode.Forbidden, reused.status)
    val body: SyncResultResponse = reused.body()
    assertEquals("error", body.status)
    assertEquals("invalid_token", body.reason)
  }

  @Test
  fun unknownTokenIsRejected() = runBlocking {
    val response = sync("not-a-real-token", authCookies())
    assertEquals(HttpStatusCode.Forbidden, response.status)
  }

  @Test
  fun expiredTokenIsRejected() = runBlocking {
    val shortLived = LocalAuthServer(port = 0, tokenTtlMillis = 0)
    shortLived.setLegacyCookieSink { cookies -> sunkCookies = cookies }
    shortLived.start()
    try {
      val response =
        sync(handshake(shortLived.boundPort).token, authCookies(), shortLived.boundPort)
      assertEquals(HttpStatusCode.Forbidden, response.status)
    } finally {
      shortLived.stop()
    }
  }

  @Test
  fun syncMissingRequiredCookiesIsRejected() = runBlocking {
    val response = sync(handshake().token, mapOf("unrelated" to "value"))
    assertEquals(HttpStatusCode.BadRequest, response.status)
    val body: SyncResultResponse = response.body()
    assertEquals("error", body.status)
    assertEquals("missing_auth_cookies", body.reason)
    assertFalse(AuthSyncState.status.value.synced)
  }

  @Test
  fun syncWithBlankCookieValueIsRejected() = runBlocking {
    val response = sync(handshake().token, mapOf("SAPISID" to "   "))
    assertEquals(HttpStatusCode.BadRequest, response.status)
    assertEquals("invalid_cookies", (response.body<SyncResultResponse>()).reason)
  }

  @Test
  fun syncWithMalformedBodyIsRejected() = runBlocking {
    val token = handshake().token
    val response =
      client.post("http://$LOOPBACK_HOST:${server.boundPort}/auth/sync") {
        setBody(TextContent("{not-json", ContentType.Application.Json))
      }
    assertEquals(HttpStatusCode.BadRequest, response.status)
    assertEquals("malformed_body", (response.body<SyncResultResponse>()).reason)
    assertEquals(HttpStatusCode.OK, sync(token, authCookies()).status)
  }

  @Test
  fun handshakeRejectsDisallowedOrigin() = runBlocking {
    val response =
      client.get("http://$LOOPBACK_HOST:${server.boundPort}/auth/handshake") {
        header(HttpHeaders.Origin, "https://evil.example")
      }
    assertEquals(HttpStatusCode.Forbidden, response.status)
  }

  @Test
  fun handshakeAllowsExtensionOrigins() = runBlocking {
    for (origin in
      listOf("chrome-extension://abcdefghijklmnop", "moz-extension://some-id", "null")) {
      val response =
        client.get("http://$LOOPBACK_HOST:${server.boundPort}/auth/handshake") {
          header(HttpHeaders.Origin, origin)
        }
      assertEquals(HttpStatusCode.OK, response.status, "origin $origin should be allowed")
    }
  }

  @Test
  fun isLoopbackAcceptsLoopbackAddresses() {
    assertTrue(isLoopback("127.0.0.1"))
    assertTrue(isLoopback("127.1.2.3"))
    assertTrue(isLoopback("::1"))
    assertTrue(isLoopback("[::1]"))
    assertTrue(isLoopback("0:0:0:0:0:0:0:1"))
    assertTrue(isLoopback("localhost"))
    assertTrue(isLoopback("LOCALHOST"))
    assertTrue(isLoopback(" 127.0.0.1 "))
  }

  @Test
  fun isLoopbackRejectsNonLoopbackAddresses() {
    assertFalse(isLoopback("8.8.8.8"))
    assertFalse(isLoopback("192.168.1.10"))
    assertFalse(isLoopback("10.0.0.1"))
    assertFalse(isLoopback("::2"))
    assertFalse(isLoopback("127.0.0.1.evil.com"))
    assertFalse(isLoopback("127.0.0"))
    assertFalse(isLoopback(""))
    assertFalse(isLoopback(null))
  }

  @Test
  fun testSyncWithVisitorDataAndDataSyncId() = runBlocking {
    var capturedPayload: AuthPayload? = null
    val customServer = LocalAuthServer(port = 0)
    customServer.cookieSink = { payload -> capturedPayload = payload }
    customServer.start()
    try {
      val token = handshake(customServer.boundPort).token
      val cookies = authCookies()
      val response =
        client.post("http://$LOOPBACK_HOST:${customServer.boundPort}/auth/sync") {
          contentType(ContentType.Application.Json)
          setBody(
            SyncRequest(
              token = token,
              cookies = cookies,
              visitorData = "visitor-xyz",
              dataSyncId = "datasync-123",
              userAgent = "test-agent",
            )
          )
        }
      assertEquals(HttpStatusCode.OK, response.status)
      val payload = assertNotNull(capturedPayload)
      assertEquals("visitor-xyz", payload.visitorData)
      assertEquals("datasync-123", payload.dataSyncId)
      assertEquals(cookies, payload.cookies)
    } finally {
      customServer.stop()
    }
  }

  private fun authCookies() =
    mapOf("SAPISID" to "sapisid-value", "HSID" to "hsid-value", "LOGIN_INFO" to "login-info")
}

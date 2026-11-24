package com.example.ctrader

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

data class CTraderOAuthTokens(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
)

class CTraderOAuthManager(
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build(),
) {
    @Volatile
    private var activeServerSocket: ServerSocket? = null

    fun buildAuthorizationUrl(clientId: String): String {
        val encodedClientId = URLEncoder.encode(clientId.trim(), "UTF-8")
        val encodedRedirect = URLEncoder.encode(REDIRECT_URI, "UTF-8")
        return "$AUTH_BASE_URL?client_id=$encodedClientId&redirect_uri=$encodedRedirect&scope=trading&product=web"
    }

    /**
     * Extracts the OAuth `code` either from a full redirect URL (e.g. http://127.0.0.1:8080/oauth?code=...)
     * or a raw authorization code string.
     */
    fun extractCodeFromInput(rawInput: String): String? {
        val trimmed = rawInput.trim()
        if (trimmed.isBlank()) return null
        if (!trimmed.contains("code=") && !trimmed.contains("://")) {
            return trimmed
        }
        val query = trimmed.substringAfter("?", "")
        if (query.isBlank()) return null
        val params = query.split("&").mapNotNull { pair ->
            val idx = pair.indexOf('=')
            if (idx <= 0) null
            else {
                val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                key to value
            }
        }.toMap()
        return params["code"]?.takeIf { it.isNotBlank() }
    }

    /**
     * Starts a local loopback HTTP listener on 127.0.0.1:8080/oauth and waits for the cTrader browser
     * callback to deliver the authorization `code`.
     */
    suspend fun awaitAuthorizationCode(timeoutMillis: Int = 240_000): Result<String> =
        withContext(Dispatchers.IO) {
            stopCallbackServer()
            try {
                val server = ServerSocket(OAUTH_PORT, 4, InetAddress.getByName("127.0.0.1")).apply {
                    reuseAddress = true
                    soTimeout = timeoutMillis
                }
                activeServerSocket = server

                while (true) {
                    val socket = server.accept()
                    socket.use { client ->
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        val requestLine = reader.readLine().orEmpty()
                        // Drain remaining headers quickly
                        while (true) {
                            val header = reader.readLine()
                            if (header.isNullOrEmpty()) break
                        }

                        // Ignore browser favicon/preflight requests
                        if (requestLine.contains("favicon.ico")) {
                            val notFound = "HTTP/1.1 404 Not Found\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
                            client.getOutputStream().write(notFound.toByteArray(StandardCharsets.UTF_8))
                            client.getOutputStream().flush()
                            continue
                        }

                        val targetPath = requestLine.split(" ").getOrNull(1).orEmpty()
                        val code = extractCodeFromInput("http://127.0.0.1:$OAUTH_PORT$targetPath")
                        val errorDesc = targetPath.substringAfter("error_description=", "")
                            .substringBefore("&")
                            .takeIf { it.isNotBlank() && targetPath.contains("error_description=") }
                            ?.let { URLDecoder.decode(it, "UTF-8") }
                            ?: targetPath.substringAfter("error=", "")
                                .substringBefore("&")
                                .takeIf { it.isNotBlank() && targetPath.contains("error=") }

                        val htmlBody = if (code != null) {
                            buildSuccessHtml()
                        } else {
                            buildErrorHtml(errorDesc ?: "Authorization was not completed.")
                        }
                        val bodyBytes = htmlBody.toByteArray(StandardCharsets.UTF_8)
                        val responseHeaders = "HTTP/1.1 200 OK\r\n" +
                            "Content-Type: text/html; charset=UTF-8\r\n" +
                            "Content-Length: ${bodyBytes.size}\r\n" +
                            "Connection: close\r\n\r\n"
                        client.getOutputStream().write(responseHeaders.toByteArray(StandardCharsets.UTF_8))
                        client.getOutputStream().write(bodyBytes)
                        client.getOutputStream().flush()

                        if (code != null) {
                            return@withContext Result.success(code)
                        }
                        if (errorDesc != null) {
                            return@withContext Result.failure(IllegalStateException("cTrader OAuth error: $errorDesc"))
                        }
                    }
                }
                @Suppress("UNREACHABLE_CODE")
                Result.failure(IllegalStateException("OAuth listener ended unexpectedly"))
            } catch (e: CancellationException) {
                throw e
            } catch (_: SocketTimeoutException) {
                Result.failure(IllegalStateException("Timed out waiting for cTrader OAuth callback on 127.0.0.1:8080"))
            } catch (e: Exception) {
                Result.failure(e)
            } finally {
                stopCallbackServer()
            }
        }

    fun stopCallbackServer() {
        try {
            activeServerSocket?.close()
        } catch (_: Exception) {
        } finally {
            activeServerSocket = null
        }
    }

    suspend fun exchangeAuthorizationCode(
        code: String,
        clientId: String,
        clientSecret: String,
    ): Result<CTraderOAuthTokens> = withContext(Dispatchers.IO) {
        try {
            val url = TOKEN_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("grant_type", "authorization_code")
                .addQueryParameter("code", code.trim())
                .addQueryParameter("redirect_uri", REDIRECT_URI)
                .addQueryParameter("client_id", clientId.trim())
                .addQueryParameter("client_secret", clientSecret.trim())
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Token exchange HTTP ${response.code}: $body")
                    )
                }
                parseTokenJson(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun refreshAccessToken(
        refreshToken: String,
        clientId: String,
        clientSecret: String,
    ): Result<CTraderOAuthTokens> = withContext(Dispatchers.IO) {
        try {
            val url = TOKEN_ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("grant_type", "refresh_token")
                .addQueryParameter("refresh_token", refreshToken.trim())
                .addQueryParameter("client_id", clientId.trim())
                .addQueryParameter("client_secret", clientSecret.trim())
                .build()

            val request = Request.Builder()
                .url(url)
                .get()
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IllegalStateException("Refresh HTTP ${response.code}: $body")
                    )
                }
                parseTokenJson(body)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun parseTokenJson(body: String): Result<CTraderOAuthTokens> {
        return try {
            val json = JSONObject(body)
            val err = json.optNonNullString("errorCode")
                .ifBlank { json.optNonNullString("error") }
            if (err.isNotBlank()) {
                val desc = json.optNonNullString("description")
                    .ifBlank { json.optNonNullString("error_description") }
                return Result.failure(
                    IllegalStateException(if (desc.isNotBlank()) "$err: $desc" else err)
                )
            }
            val access = json.optNonNullString("accessToken")
                .ifBlank { json.optNonNullString("access_token") }
            val refresh = json.optNonNullString("refreshToken")
                .ifBlank { json.optNonNullString("refresh_token") }
            val expiresIn = if (!json.isNull("expiresIn")) {
                json.optLong("expiresIn", 2_592_000L)
            } else if (!json.isNull("expires_in")) {
                json.optLong("expires_in", 2_592_000L)
            } else {
                2_592_000L
            }
            if (access.isBlank()) {
                return Result.failure(IllegalStateException("Empty access token returned from cTrader"))
            }
            Result.success(
                CTraderOAuthTokens(
                    accessToken = access,
                    refreshToken = refresh,
                    expiresInSeconds = expiresIn,
                )
            )
        } catch (e: Exception) {
            Result.failure(
                IllegalStateException(
                    "Invalid token response from cTrader: ${e.message ?: body.take(120)}"
                )
            )
        }
    }

    private fun JSONObject.optNonNullString(key: String): String {
        if (!has(key) || isNull(key)) return ""
        val value = optString(key, "")
        return if (value.equals("null", ignoreCase = true)) "" else value
    }

    private fun buildSuccessHtml(): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>cTrader Connected</title>
          <style>
            body { background:#0B1220; color:#F8FAFC; font-family:system-ui,sans-serif; display:flex; align-items:center; justify-content:center; min-height:100vh; margin:0; padding:24px; text-align:center; }
            .card { background:#121B2E; border:1px solid #1E293B; border-radius:18px; padding:28px; max-width:380px; }
            .badge { width:56px; height:56px; border-radius:16px; background:#10B981; color:#fff; display:inline-flex; align-items:center; justify-content:center; font-size:28px; margin-bottom:14px; }
            h2 { margin:0 0 8px; font-size:20px; }
            p { color:#94A3B8; font-size:14px; line-height:1.5; margin:0; }
          </style>
        </head>
        <body>
          <div class="card">
            <div class="badge">✓</div>
            <h2>cTrader Account Approved</h2>
            <p>You can now return to <strong>Management Risk and Trade</strong>. Your live prices and account data are loading.</p>
          </div>
        </body>
        </html>
    """.trimIndent()

    private fun buildErrorHtml(message: String): String = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1.0" />
          <title>cTrader Authorization</title>
          <style>
            body { background:#0B1220; color:#F8FAFC; font-family:system-ui,sans-serif; display:flex; align-items:center; justify-content:center; min-height:100vh; margin:0; padding:24px; text-align:center; }
            .card { background:#121B2E; border:1px solid #1E293B; border-radius:18px; padding:28px; max-width:380px; }
            h2 { margin:0 0 8px; color:#FB7185; font-size:20px; }
            p { color:#94A3B8; font-size:14px; line-height:1.5; margin:0; }
          </style>
        </head>
        <body>
          <div class="card">
            <h2>Authorization Failed</h2>
            <p>$message</p>
          </div>
        </body>
        </html>
    """.trimIndent()

    companion object {
        const val OAUTH_PORT = 8080
        const val REDIRECT_URI = "http://127.0.0.1:8080/oauth"
        const val AUTH_BASE_URL = "https://id.ctrader.com/my/settings/openapi/grantingaccess/"
        const val TOKEN_ENDPOINT = "https://openapi.ctrader.com/apps/token"
        const val CTRADER_PORTAL_URL = "https://openapi.ctrader.com/apps"
    }
}

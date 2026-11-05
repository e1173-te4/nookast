package ru.e1173.nookast.auth

import ru.e1173.nookast.core.network.HttpClients
import java.nio.charset.StandardCharsets
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

data class AuthSession(
    val serverUrl: String,
    val userId: Int,
    val apiKey: String,
)

sealed interface LoginOutcome {
    data class Success(val session: AuthSession) : LoginOutcome

    data class MfaRequired(
        val serverUrl: String,
        val userId: Int,
        val sessionToken: String,
    ) : LoginOutcome
}

class LoginClient(
    private val httpClient: OkHttpClient = HttpClients.pinePods,
) {
    fun login(serverUrl: String, username: String, password: String): LoginOutcome {
        val baseUrl = ServerAddress.normalize(serverUrl).getOrThrow()
        if (username.isBlank()) throw LoginFailure(LoginError.ENTER_USERNAME)
        if (password.isEmpty()) throw LoginFailure(LoginError.ENTER_PASSWORD)

        val endpoint = baseUrl.resolve("/api/data/get_key")
            ?: throw LoginFailure(LoginError.BAD_ADDRESS)
        val request = Request.Builder()
            .url(endpoint)
            .header(
                "Authorization",
                Credentials.basic(username, password, StandardCharsets.UTF_8),
            )
            .header("Accept", "application/json")
            .get()
            .build()

        return httpClient.newCall(request).execute().use { response ->
            when (response.code) {
                401 -> throw LoginFailure(LoginError.WRONG_CREDENTIALS)
                in 300..399 -> throw LoginFailure(LoginError.REDIRECT)
            }
            if (!response.isSuccessful) {
                throw LoginFailure(LoginError.SERVER_ERROR, response.code)
            }

            val body = response.body.string()
            val json = JSONObject(body)
            val userId = json.optInt("user_id", -1)
            if (userId < 0) {
                throw LoginFailure(LoginError.INCOMPLETE_ANSWER)
            }

            val apiKey = json.optNullableString("retrieved_key")
            val mfaRequired = json.optBoolean("mfa_required", false)
            when {
                !apiKey.isNullOrBlank() -> LoginOutcome.Success(
                    AuthSession(baseUrl.toString(), userId, apiKey),
                )

                mfaRequired -> {
                    val token = json.optNullableString("mfa_session_token")
                        ?: throw LoginFailure(LoginError.INCOMPLETE_ANSWER)
                    LoginOutcome.MfaRequired(baseUrl.toString(), userId, token)
                }

                else -> throw LoginFailure(LoginError.INCOMPLETE_ANSWER)
            }
        }
    }

    fun verifyMfa(
        serverUrl: String,
        userId: Int,
        sessionToken: String,
        code: String,
    ): AuthSession {
        val baseUrl = ServerAddress.normalize(serverUrl).getOrThrow()
        if (code.isBlank()) throw LoginFailure(LoginError.ENTER_CODE)

        val endpoint = baseUrl.resolve("/api/data/verify_mfa_and_get_key")
            ?: throw LoginFailure(LoginError.BAD_ADDRESS)
        val payload = JSONObject()
            .put("mfa_session_token", sessionToken)
            .put("mfa_code", code)
            .toString()
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()

        return httpClient.newCall(request).execute().use { response ->
            if (response.code == 401) {
                throw LoginFailure(LoginError.WRONG_CODE)
            }
            if (!response.isSuccessful) {
                throw LoginFailure(LoginError.SERVER_ERROR, response.code)
            }
            val json = JSONObject(response.body.string())
            if (!json.optBoolean("verified", false)) {
                throw LoginFailure(LoginError.WRONG_CODE)
            }
            val apiKey = json.optNullableString("retrieved_key")
                ?: throw LoginFailure(LoginError.INCOMPLETE_ANSWER)
            AuthSession(baseUrl.toString(), userId, apiKey)
        }
    }

    private fun JSONObject.optNullableString(name: String): String? =
        if (isNull(name)) null else optString(name).takeIf { it.isNotBlank() }

    private companion object {
        val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/**
 * Why signing in failed (U-09): a reason, not a sentence, so the screen words it in the app's
 * language when it shows it. [httpCode] is the server's status for [LoginError.SERVER_ERROR].
 */
enum class LoginError {
    ENTER_USERNAME, ENTER_PASSWORD, ENTER_CODE, BAD_ADDRESS, ONLY_HTTP_HTTPS, CREDENTIALS_IN_ADDRESS,
    WRONG_CREDENTIALS, REDIRECT, SERVER_ERROR, INCOMPLETE_ANSWER, WRONG_CODE, MFA_EXPIRED, UNREACHABLE, FAILED,
}

class LoginFailure(val reason: LoginError, val httpCode: Int? = null) : Exception(reason.name)

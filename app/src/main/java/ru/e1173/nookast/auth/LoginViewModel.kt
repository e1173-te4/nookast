package ru.e1173.nookast.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.core.session.SessionLossReason
import ru.e1173.nookast.R

enum class LoginStage {
    SIGN_IN,
    LOADING,
    MFA,
    CONNECTED,
}

/** A message of the sign-in screen as a resource, worded when shown (U-09). */
data class LoginMessage(val text: Int, val arg: String? = null)

data class LoginUiState(
    val stage: LoginStage = LoginStage.SIGN_IN,
    val message: LoginMessage? = null,
    val serverUrl: String? = null,
    val userId: Int? = null,
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val loginClient = LoginClient()
    private val sessionStore = application.appGraph.session
    private var pendingMfa: LoginOutcome.MfaRequired? = null

    /** The password being typed: kept across rotation here rather than in saved state (U-09). */
    val passwordDraft = androidx.compose.runtime.mutableStateOf("")

    private val _state = MutableStateFlow(
        sessionStore.load()?.let { session ->
            LoginUiState(
                stage = LoginStage.CONNECTED,
                serverUrl = session.serverUrl,
                userId = session.userId,
            )
        } ?: LoginUiState(),
    )
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    init {
        // A session that becomes undecryptable (key invalidated, tampered store) must surface as a
        // sign-in prompt instead of a silently "connected" UI whose sync paths all return early.
        viewModelScope.launch {
            sessionStore.state.collect { session ->
                if (session.session == null &&
                    session.lossReason == SessionLossReason.CORRUPT &&
                    _state.value.stage == LoginStage.CONNECTED
                ) {
                    _state.value = LoginUiState(
                        message = LoginMessage(R.string.session_lost_sign_in_again),
                    )
                }
            }
        }
    }

    fun signIn(serverUrl: String, username: String, password: String) {
        if (_state.value.stage == LoginStage.LOADING) return
        _state.value = LoginUiState(stage = LoginStage.LOADING)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    loginClient.login(serverUrl, username, password)
                }
            }.onSuccess { outcome ->
                when (outcome) {
                    is LoginOutcome.Success -> finishLogin(outcome.session)
                    is LoginOutcome.MfaRequired -> {
                        pendingMfa = outcome
                        _state.value = LoginUiState(
                            stage = LoginStage.MFA,
                            serverUrl = outcome.serverUrl,
                            userId = outcome.userId,
                        )
                    }
                }
            }.onFailure { error ->
                _state.value = LoginUiState(
                    stage = LoginStage.SIGN_IN,
                    message = error.toUserMessage(),
                )
            }
        }
    }

    fun verifyMfa(code: String) {
        val pending = pendingMfa ?: run {
            _state.value = LoginUiState(
                stage = LoginStage.SIGN_IN,
                message = LoginMessage(LoginError.MFA_EXPIRED.text()),
            )
            return
        }
        if (_state.value.stage == LoginStage.LOADING) return
        _state.value = _state.value.copy(stage = LoginStage.LOADING, message = null)

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    loginClient.verifyMfa(
                        serverUrl = pending.serverUrl,
                        userId = pending.userId,
                        sessionToken = pending.sessionToken,
                        code = code,
                    )
                }
            }.onSuccess(::finishLogin)
                .onFailure { error ->
                    _state.value = LoginUiState(
                        stage = LoginStage.MFA,
                        message = error.toUserMessage(),
                        serverUrl = pending.serverUrl,
                        userId = pending.userId,
                    )
                }
        }
    }

    fun signOut() {
        pendingMfa = null
        if (sessionStore.clear()) {
            _state.value = LoginUiState()
        } else {
            _state.value = _state.value.copy(
                message = LoginMessage(R.string.secure_storage_error),
            )
        }
    }

    private fun finishLogin(session: AuthSession) {
        val stored = runCatching { sessionStore.save(session) }.isSuccess
        if (!stored) {
            pendingMfa = null
            _state.value = LoginUiState(
                stage = LoginStage.SIGN_IN,
                message = LoginMessage(R.string.secure_storage_error),
            )
            return
        }
        // Whose data the database holds is settled before the first intent of this session.
        getApplication<Application>().appGraph.syncScheduler.onSessionChanged()
        pendingMfa = null
        _state.value = LoginUiState(
            stage = LoginStage.CONNECTED,
            message = LoginMessage(R.string.connection_confirmed),
            serverUrl = session.serverUrl,
            userId = session.userId,
        )
    }

    private fun Throwable.toUserMessage(): LoginMessage = when (this) {
        is LoginFailure -> LoginMessage(reason.text(), httpCode?.toString())
        is IOException -> LoginMessage(LoginError.UNREACHABLE.text())
        else -> LoginMessage(LoginError.FAILED.text())
    }
}

internal fun LoginError.text(): Int = when (this) {
    LoginError.ENTER_USERNAME -> R.string.login_error_enter_username
    LoginError.ENTER_PASSWORD -> R.string.login_error_enter_password
    LoginError.ENTER_CODE -> R.string.login_error_enter_code
    LoginError.BAD_ADDRESS -> R.string.login_error_bad_address
    LoginError.ONLY_HTTP_HTTPS -> R.string.login_error_only_http
    LoginError.CREDENTIALS_IN_ADDRESS -> R.string.login_error_credentials_in_address
    LoginError.WRONG_CREDENTIALS -> R.string.login_error_wrong_credentials
    LoginError.REDIRECT -> R.string.login_error_redirect
    LoginError.SERVER_ERROR -> R.string.login_error_server
    LoginError.INCOMPLETE_ANSWER -> R.string.login_error_incomplete
    LoginError.WRONG_CODE -> R.string.login_error_wrong_code
    LoginError.MFA_EXPIRED -> R.string.login_error_mfa_expired
    LoginError.UNREACHABLE -> R.string.login_error_unreachable
    LoginError.FAILED -> R.string.login_error_failed
}

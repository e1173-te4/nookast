package ru.e1173.nookast.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.auth.AuthSession
import ru.e1173.nookast.data.remote.ServerSettings
import ru.e1173.nookast.data.remote.ServerSettingsClient

data class ServerSettingsUiState(
    val settings: ServerSettings? = null,
    val loading: Boolean = false,
    val saving: Boolean = false,
    /** The last load or save failed: the panel shows the server values as unavailable. */
    val failed: Boolean = false,
)

/**
 * The "Stored on the server" panel. A change is shown at once and rolled back if the server
 * refuses it, so the panel never claims a value the server does not hold.
 */
class ServerSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val sessionStore = application.appGraph.session
    private val client = ServerSettingsClient()
    private val mutableState = MutableStateFlow(ServerSettingsUiState())
    val state: StateFlow<ServerSettingsUiState> = mutableState.asStateFlow()

    fun load() {
        if (mutableState.value.loading) return
        mutableState.update { it.copy(loading = true) }
        viewModelScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                val session = sessionStore.load() ?: return@withContext null
                runCatching { client.fetch(session) }.getOrNull()
            }
            mutableState.update {
                it.copy(settings = loaded ?: it.settings, loading = false, failed = loaded == null)
            }
        }
    }

    fun setPlaybackSpeed(speed: Double) = change({ copy(playbackSpeed = speed) }) { client.setPlaybackSpeed(it, speed) }

    fun setAutoCompleteSeconds(seconds: Int) =
        change({ copy(autoCompleteSeconds = seconds) }) { client.setAutoCompleteSeconds(it, seconds) }

    fun setTimezone(timezone: String) = change({ copy(timezone = timezone) }) { client.setTimezone(it, timezone) }

    fun setHourPref(hourPref: Int) = change({ copy(hourPref = hourPref) }) { client.setHourPref(it, hourPref) }

    fun setDateFormat(format: String) = change({ copy(dateFormat = format) }) { client.setDateFormat(it, format) }

    private fun change(apply: ServerSettings.() -> ServerSettings, send: (AuthSession) -> Unit) {
        val before = mutableState.value.settings ?: return
        mutableState.update { it.copy(settings = before.apply(), saving = true) }
        viewModelScope.launch {
            val ok = withContext(Dispatchers.IO) {
                val session = sessionStore.load() ?: return@withContext false
                runCatching { send(session) }.isSuccess
            }
            mutableState.update {
                if (ok) it.copy(saving = false, failed = false) else it.copy(settings = before, saving = false, failed = true)
            }
        }
    }
}

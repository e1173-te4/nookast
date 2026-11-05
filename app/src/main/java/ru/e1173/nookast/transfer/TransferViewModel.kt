package ru.e1173.nookast.transfer

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.e1173.nookast.BuildConfig
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.sync.LocalSequenceGenerator

/** Where the transfer stands, for the panel in Settings → Server. */
internal data class TransferUiState(
    /** Intents not yet on the server; the export waits until they are. */
    val pending: Int? = null,
    val deferred: Int = 0,
    val busy: Boolean = false,
    val message: TransferMessage? = null,
)

internal sealed interface TransferMessage {
    data object Exported : TransferMessage
    data class Imported(val settings: Int, val intents: Int, val otherAccount: Boolean) : TransferMessage
    data class Failed(val error: TransferError?) : TransferMessage
    data object PendingFirst : TransferMessage
}

/**
 * The transfer to the release package (plan 8.2): the debug app exports, the release app imports.
 * Both directions live in both builds, so the file can also restore a reinstall.
 */
internal class TransferViewModel(application: Application) : AndroidViewModel(application) {
    private val graph = application.appGraph
    private val _state = MutableStateFlow(TransferUiState())
    val state: StateFlow<TransferUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val (pending, deferred) = withContext(Dispatchers.IO) {
                graph.syncRepository.pendingSummary().total to graph.database.deferredEpisodeIntentDao().pendingCount()
            }
            _state.value = _state.value.copy(pending = pending, deferred = deferred)
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /** Writes the transfer file to [uri]; refused while anything still waits for the server. */
    fun export(uri: Uri) = run {
        val context = getApplication<Application>()
        if (graph.syncRepository.pendingSummary().total > 0) return@run TransferMessage.PendingFirst
        val session = graph.session.load()
        val bundle = TransferBundle(
            formatVersion = TransferFormat.VERSION,
            createdAtMs = System.currentTimeMillis(),
            sourcePackage = context.packageName,
            sourceVersion = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            serverUrl = session?.serverUrl,
            userId = session?.userId,
            preferences = TransferFormat.readPreferences(context),
            deferredIntents = graph.database.deferredEpisodeIntentDao().all(),
        )
        val stream = context.contentResolver.openOutputStream(uri, "wt") ?: return@run TransferMessage.Failed(null)
        stream.use { it.write(TransferFormat.encode(bundle).toByteArray(Charsets.UTF_8)) }
        TransferMessage.Exported
    }

    /**
     * Applies the file at [uri]: the settings always; the waiting RSS intents only into the same
     * account, never over an intent this install already has, and with the sequence counter moved
     * past them so a later local intent is always the newer one.
     */
    fun import(uri: Uri) = run {
        val context = getApplication<Application>()
        val text = context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: return@run TransferMessage.Failed(null)
        val bundle = try {
            TransferFormat.decode(text)
        } catch (error: TransferException) {
            return@run TransferMessage.Failed(error.reason)
        }
        TransferFormat.writePreferences(context, bundle.preferences)
        val session = graph.session.load()
        val sameAccount = session != null && bundle.userId == session.userId &&
            bundle.serverUrl?.trimEnd('/') == session.serverUrl.trimEnd('/')
        var intents = 0
        if (sameAccount && bundle.deferredIntents.isNotEmpty()) {
            val newest = bundle.deferredIntents.maxOf { it.sequence }
            LocalSequenceGenerator(context).next(nowEpochMs = maxOf(System.currentTimeMillis(), newest + 1))
            val dao = graph.database.deferredEpisodeIntentDao()
            bundle.deferredIntents.forEach { if (dao.insertIfAbsent(it)) intents++ }
        }
        TransferMessage.Imported(
            settings = bundle.preferences.values.sumOf { it.size },
            intents = intents,
            otherAccount = !sameAccount && bundle.deferredIntents.isNotEmpty(),
        )
    }

    private fun run(block: suspend () -> TransferMessage) {
        viewModelScope.launch {
            _state.value = _state.value.copy(busy = true, message = null)
            val message = runCatching { withContext(Dispatchers.IO) { block() } }.getOrElse { TransferMessage.Failed(null) }
            _state.value = _state.value.copy(busy = false, message = message)
            refresh()
        }
    }
}

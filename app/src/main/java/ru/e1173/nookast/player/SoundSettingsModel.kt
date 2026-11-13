package ru.e1173.nookast.player

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** What the sound popover shows (З13). */
data class SoundUi(
    val global: GlobalSound = GlobalSound(),
    val podcast: PodcastSound = PodcastSound(),
    val session: SoundSessionState = SoundSessionState(),
)

/** The sound popover's state and changes; the service applies them through the same storage. */
class SoundSettingsModel(application: Application) : AndroidViewModel(application) {
    private val store = SoundSettingsStore.open(application)
    private val preferences = SoundSettingsStore.preferences(application)
    private val version = MutableStateFlow(0)
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> version.value++ }

    init {
        preferences.registerOnSharedPreferenceChangeListener(listener)
    }

    val ui: StateFlow<SoundUi> = combine(version, SoundSession.state) { _, session ->
        SoundUi(store.global(), session.slot?.let(store::podcast) ?: PodcastSound(), session)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, SoundUi())

    fun setVolume(db: Int) = changePodcast { it.copy(volumeDb = db) }

    fun setMono(on: Boolean) = changePodcast { it.copy(mono = on) }

    fun changeGlobal(change: (GlobalSound) -> GlobalSound) = store.setGlobal(change(store.global()))

    fun compare(held: Boolean) = SoundSession.compare(held)

    private fun changePodcast(change: (PodcastSound) -> PodcastSound) {
        val slot = SoundSession.state.value.slot ?: return
        store.setPodcast(slot, change(store.podcast(slot)))
    }

    override fun onCleared() {
        preferences.unregisterOnSharedPreferenceChangeListener(listener)
    }
}

package ru.e1173.nookast.player

import android.content.Context
import androidx.core.content.edit

/**
 * Per podcast, on this device only (DEC-069, default until the user decides otherwise): «Этот подкаст
 * всегда через сервер», and whether its host already failed once (the second failure offers the
 * switch). Kept in `playback_preferences`, so the transfer file carries them; never synchronised, so
 * Room and the outbox are not involved. Keys carry the account's fingerprint: another account on the
 * same device starts clean.
 */
class PodcastRouteStore(context: Context) {
    private val preferences = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    fun alwaysViaServer(account: String, podcastId: Int): Boolean = preferences.getBoolean(key(ALWAYS, account, podcastId), false)

    fun setAlwaysViaServer(account: String, podcastId: Int, on: Boolean) = preferences.edit {
        if (on) putBoolean(key(ALWAYS, account, podcastId), true) else remove(key(ALWAYS, account, podcastId))
    }

    fun hostFailedBefore(account: String, podcastId: Int): Boolean = preferences.getBoolean(key(FAILED, account, podcastId), false)

    fun markHostFailed(account: String, podcastId: Int) = preferences.edit { putBoolean(key(FAILED, account, podcastId), true) }

    private fun key(kind: String, account: String, podcastId: Int) = "$kind.$account.$podcastId"

    private companion object {
        const val NAME = "playback_preferences"
        const val ALWAYS = "always_via_server"
        const val FAILED = "host_failed"
    }
}

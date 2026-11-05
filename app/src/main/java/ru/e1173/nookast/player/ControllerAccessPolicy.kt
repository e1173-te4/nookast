package ru.e1173.nookast.player

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** What a media controller may do with the session (finding X-02, plan 4.2). */
internal enum class ControllerAccess {
    /** Browse the library, replace the playlist, all player commands. */
    FULL,

    /** Play, pause, seek, next/previous and read what plays; nothing that changes the queue. */
    TRANSPORT,
}

/**
 * Full access for this app, trusted controllers (system apps and holders of
 * MEDIA_CONTENT_CONTROL: System UI, Bluetooth, the head-unit shell), the media notification and
 * known car companions. Any other installed app gets transport only: it can no longer replace
 * the playlist with its own items or browse the library.
 */
internal fun controllerAccess(
    packageName: String,
    isOwnApp: Boolean,
    isTrusted: Boolean,
    isMediaNotificationController: Boolean,
): ControllerAccess = when {
    isOwnApp || isTrusted || isMediaNotificationController -> ControllerAccess.FULL
    packageName in FULL_ACCESS_PACKAGES -> ControllerAccess.FULL
    else -> ControllerAccess.TRANSPORT
}

/** Car companions that are ordinary apps on a phone, so not "trusted" by the platform. */
internal val FULL_ACCESS_PACKAGES = setOf(
    "com.google.android.projection.gearhead", // Android Auto
)

internal val TRANSPORT_PLAYER_COMMANDS: Player.Commands by lazy { transportPlayerCommands() }

@OptIn(UnstableApi::class)
private fun transportPlayerCommands(): Player.Commands =
    Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_PREPARE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_BACK,
            Player.COMMAND_SEEK_FORWARD,
            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_DEFAULT_POSITION,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_AUDIO_ATTRIBUTES,
            Player.COMMAND_RELEASE,
        )
        .build()

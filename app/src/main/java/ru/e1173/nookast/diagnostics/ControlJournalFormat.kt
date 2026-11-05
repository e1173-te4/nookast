package ru.e1173.nookast.diagnostics

import android.annotation.SuppressLint
import android.media.AudioDeviceInfo
import android.os.Bundle
import android.view.InputDevice
import android.view.KeyEvent
import androidx.media3.common.Player

/**
 * Pure formatting for [ControlJournal] records. Everything here describes hardware, packages
 * and command codes; nothing reads titles, URLs or credentials.
 */
internal object ControlJournalFormat {

    /**
     * A key event in full detail. Printing keys seen by the activity are redacted ([redactText])
     * because they may be characters typed into a text field (for example a password).
     */
    fun keyEvent(event: KeyEvent, redactText: Boolean = false): String = buildString {
        append("action=").append(keyAction(event.action))
        if (redactText && event.isPrintingKey) {
            append(" key=<printing key redacted>")
        } else {
            append(" key=").append(keyName(event.keyCode)).append('(').append(event.keyCode).append(')')
            append(" scan=").append(event.scanCode)
        }
        append(" repeat=").append(event.repeatCount)
        append(" flags=0x").append(Integer.toHexString(event.flags))
        append(" meta=0x").append(Integer.toHexString(event.metaState))
        append(" source=0x").append(Integer.toHexString(event.source))
        append(" deviceId=").append(event.deviceId)
        val device = runCatching { InputDevice.getDevice(event.deviceId) }.getOrNull()
        if (device != null) {
            append(" device=\"").append(device.name).append('"')
            append(" vendor=0x").append(Integer.toHexString(device.vendorId))
            append(" product=0x").append(Integer.toHexString(device.productId))
            append(" external=").append(device.isExternalCompat())
        }
        append(" eventTime=").append(event.eventTime)
        append(" downTime=").append(event.downTime)
    }

    /**
     * Symbolic key name. Some environments (Robolectric, some OEM builds) return only the number
     * from [KeyEvent.keyCodeToString]; the keys that matter in a car then come from a local table.
     */
    fun keyName(keyCode: Int): String {
        val system = runCatching { KeyEvent.keyCodeToString(keyCode) }.getOrNull()
            ?.removePrefix("KEYCODE_")
        if (system != null && system.toIntOrNull() == null) return system
        return CAR_KEY_NAMES[keyCode] ?: system ?: keyCode.toString()
    }

    fun keyAction(action: Int): String = when (action) {
        KeyEvent.ACTION_DOWN -> "DOWN"
        KeyEvent.ACTION_UP -> "UP"
        KeyEvent.ACTION_MULTIPLE -> "MULTIPLE"
        else -> action.toString()
    }

    /** Sorted bundle keys only; values may carry anything and are never written. */
    fun bundleKeys(bundle: Bundle?): String =
        bundle?.keySet()?.sorted()?.joinToString(prefix = "[", postfix = "]") ?: "[]"

    /** Media IDs are episode IDs; anything that looks like a URI is redacted. */
    fun mediaId(id: String?): String = when {
        id == null -> "null"
        id.isEmpty() -> "\"\""
        "://" in id || id.startsWith("content:") || id.startsWith("file:") -> "<uri redacted>"
        else -> id.take(MAX_ID_LENGTH)
    }

    fun mediaIds(ids: List<String?>): String {
        val shown = ids.take(MAX_IDS).joinToString { mediaId(it) }
        val rest = ids.size - MAX_IDS
        return if (rest > 0) "[$shown, +$rest]" else "[$shown]"
    }

    fun playerCommand(command: Int): String = PLAYER_COMMANDS[command] ?: "COMMAND_$command"

    fun playWhenReadyReason(reason: Int): String = when (reason) {
        Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST -> "USER_REQUEST"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS -> "AUDIO_FOCUS_LOSS"
        Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_BECOMING_NOISY -> "AUDIO_BECOMING_NOISY"
        Player.PLAY_WHEN_READY_CHANGE_REASON_REMOTE -> "REMOTE"
        Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM -> "END_OF_MEDIA_ITEM"
        Player.PLAY_WHEN_READY_CHANGE_REASON_SUPPRESSED_TOO_LONG -> "SUPPRESSED_TOO_LONG"
        else -> reason.toString()
    }

    fun suppressionReason(reason: Int): String = when (reason) {
        Player.PLAYBACK_SUPPRESSION_REASON_NONE -> "NONE"
        Player.PLAYBACK_SUPPRESSION_REASON_TRANSIENT_AUDIO_FOCUS_LOSS -> "TRANSIENT_AUDIO_FOCUS_LOSS"
        Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_ROUTE -> "UNSUITABLE_AUDIO_ROUTE"
        Player.PLAYBACK_SUPPRESSION_REASON_UNSUITABLE_AUDIO_OUTPUT -> "UNSUITABLE_AUDIO_OUTPUT"
        Player.PLAYBACK_SUPPRESSION_REASON_SCRUBBING -> "SCRUBBING"
        else -> reason.toString()
    }

    fun transitionReason(reason: Int): String = when (reason) {
        Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT -> "REPEAT"
        Player.MEDIA_ITEM_TRANSITION_REASON_AUTO -> "AUTO"
        Player.MEDIA_ITEM_TRANSITION_REASON_SEEK -> "SEEK"
        Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED -> "PLAYLIST_CHANGED"
        else -> reason.toString()
    }

    fun discontinuityReason(reason: Int): String = when (reason) {
        Player.DISCONTINUITY_REASON_AUTO_TRANSITION -> "AUTO_TRANSITION"
        Player.DISCONTINUITY_REASON_SEEK -> "SEEK"
        Player.DISCONTINUITY_REASON_SEEK_ADJUSTMENT -> "SEEK_ADJUSTMENT"
        Player.DISCONTINUITY_REASON_SKIP -> "SKIP"
        Player.DISCONTINUITY_REASON_REMOVE -> "REMOVE"
        Player.DISCONTINUITY_REASON_INTERNAL -> "INTERNAL"
        Player.DISCONTINUITY_REASON_SILENCE_SKIP -> "SILENCE_SKIP"
        else -> reason.toString()
    }

    fun playbackState(state: Int): String = when (state) {
        Player.STATE_IDLE -> "IDLE"
        Player.STATE_BUFFERING -> "BUFFERING"
        Player.STATE_READY -> "READY"
        Player.STATE_ENDED -> "ENDED"
        else -> state.toString()
    }

    fun audioDevice(device: AudioDeviceInfo): String = buildString {
        append("id=").append(device.id)
        append(" type=").append(audioDeviceType(device.type))
        append(" name=\"").append(device.productName).append('"')
        append(" sink=").append(device.isSink)
        append(" source=").append(device.isSource)
    }

    fun audioDeviceType(type: Int): String = AUDIO_DEVICE_TYPES[type] ?: "TYPE_$type"

    private fun InputDevice.isExternalCompat(): String =
        if (android.os.Build.VERSION.SDK_INT >= 29) isExternal.toString() else "n/a"

    private const val MAX_IDS = 10
    private const val MAX_ID_LENGTH = 80

    private val CAR_KEY_NAMES: Map<Int, String> = mapOf(
        KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE to "MEDIA_PLAY_PAUSE",
        KeyEvent.KEYCODE_MEDIA_PLAY to "MEDIA_PLAY",
        KeyEvent.KEYCODE_MEDIA_PAUSE to "MEDIA_PAUSE",
        KeyEvent.KEYCODE_MEDIA_STOP to "MEDIA_STOP",
        KeyEvent.KEYCODE_MEDIA_NEXT to "MEDIA_NEXT",
        KeyEvent.KEYCODE_MEDIA_PREVIOUS to "MEDIA_PREVIOUS",
        KeyEvent.KEYCODE_MEDIA_REWIND to "MEDIA_REWIND",
        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD to "MEDIA_FAST_FORWARD",
        KeyEvent.KEYCODE_MEDIA_SKIP_FORWARD to "MEDIA_SKIP_FORWARD",
        KeyEvent.KEYCODE_MEDIA_SKIP_BACKWARD to "MEDIA_SKIP_BACKWARD",
        KeyEvent.KEYCODE_MEDIA_STEP_FORWARD to "MEDIA_STEP_FORWARD",
        KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD to "MEDIA_STEP_BACKWARD",
        KeyEvent.KEYCODE_HEADSETHOOK to "HEADSETHOOK",
        KeyEvent.KEYCODE_MUTE to "MUTE",
        KeyEvent.KEYCODE_VOLUME_MUTE to "VOLUME_MUTE",
        KeyEvent.KEYCODE_VOLUME_UP to "VOLUME_UP",
        KeyEvent.KEYCODE_VOLUME_DOWN to "VOLUME_DOWN",
        KeyEvent.KEYCODE_CALL to "CALL",
        KeyEvent.KEYCODE_ENDCALL to "ENDCALL",
        KeyEvent.KEYCODE_VOICE_ASSIST to "VOICE_ASSIST",
        KeyEvent.KEYCODE_SEARCH to "SEARCH",
        KeyEvent.KEYCODE_DPAD_UP to "DPAD_UP",
        KeyEvent.KEYCODE_DPAD_DOWN to "DPAD_DOWN",
        KeyEvent.KEYCODE_DPAD_LEFT to "DPAD_LEFT",
        KeyEvent.KEYCODE_DPAD_RIGHT to "DPAD_RIGHT",
        KeyEvent.KEYCODE_DPAD_CENTER to "DPAD_CENTER",
        KeyEvent.KEYCODE_ENTER to "ENTER",
        KeyEvent.KEYCODE_BACK to "BACK",
        KeyEvent.KEYCODE_HOME to "HOME",
        KeyEvent.KEYCODE_MENU to "MENU",
        KeyEvent.KEYCODE_CHANNEL_UP to "CHANNEL_UP",
        KeyEvent.KEYCODE_CHANNEL_DOWN to "CHANNEL_DOWN",
    )

    private val PLAYER_COMMANDS: Map<Int, String> = mapOf(
        Player.COMMAND_PLAY_PAUSE to "PLAY_PAUSE",
        Player.COMMAND_PREPARE to "PREPARE",
        Player.COMMAND_STOP to "STOP",
        Player.COMMAND_SEEK_TO_DEFAULT_POSITION to "SEEK_TO_DEFAULT_POSITION",
        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM to "SEEK_IN_CURRENT_MEDIA_ITEM",
        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM to "SEEK_TO_PREVIOUS_MEDIA_ITEM",
        Player.COMMAND_SEEK_TO_PREVIOUS to "SEEK_TO_PREVIOUS",
        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM to "SEEK_TO_NEXT_MEDIA_ITEM",
        Player.COMMAND_SEEK_TO_NEXT to "SEEK_TO_NEXT",
        Player.COMMAND_SEEK_TO_MEDIA_ITEM to "SEEK_TO_MEDIA_ITEM",
        Player.COMMAND_SEEK_BACK to "SEEK_BACK",
        Player.COMMAND_SEEK_FORWARD to "SEEK_FORWARD",
        Player.COMMAND_SET_SPEED_AND_PITCH to "SET_SPEED_AND_PITCH",
        Player.COMMAND_SET_SHUFFLE_MODE to "SET_SHUFFLE_MODE",
        Player.COMMAND_SET_REPEAT_MODE to "SET_REPEAT_MODE",
        Player.COMMAND_GET_CURRENT_MEDIA_ITEM to "GET_CURRENT_MEDIA_ITEM",
        Player.COMMAND_GET_TIMELINE to "GET_TIMELINE",
        Player.COMMAND_GET_METADATA to "GET_METADATA",
        Player.COMMAND_SET_PLAYLIST_METADATA to "SET_PLAYLIST_METADATA",
        Player.COMMAND_SET_MEDIA_ITEM to "SET_MEDIA_ITEM",
        Player.COMMAND_CHANGE_MEDIA_ITEMS to "CHANGE_MEDIA_ITEMS",
        Player.COMMAND_GET_AUDIO_ATTRIBUTES to "GET_AUDIO_ATTRIBUTES",
        Player.COMMAND_GET_VOLUME to "GET_VOLUME",
        Player.COMMAND_GET_DEVICE_VOLUME to "GET_DEVICE_VOLUME",
        Player.COMMAND_SET_VOLUME to "SET_VOLUME",
        Player.COMMAND_SET_DEVICE_VOLUME_WITH_FLAGS to "SET_DEVICE_VOLUME_WITH_FLAGS",
        Player.COMMAND_ADJUST_DEVICE_VOLUME_WITH_FLAGS to "ADJUST_DEVICE_VOLUME_WITH_FLAGS",
        Player.COMMAND_SET_AUDIO_ATTRIBUTES to "SET_AUDIO_ATTRIBUTES",
        Player.COMMAND_SET_VIDEO_SURFACE to "SET_VIDEO_SURFACE",
        Player.COMMAND_GET_TEXT to "GET_TEXT",
        Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS to "SET_TRACK_SELECTION_PARAMETERS",
        Player.COMMAND_GET_TRACKS to "GET_TRACKS",
        Player.COMMAND_RELEASE to "RELEASE",
    )

    // Constants are inlined ints; newer types simply never occur on older devices.
    @SuppressLint("InlinedApi")
    private val AUDIO_DEVICE_TYPES: Map<Int, String> = mapOf(
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE to "BUILTIN_EARPIECE",
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER to "BUILTIN_SPEAKER",
        AudioDeviceInfo.TYPE_WIRED_HEADSET to "WIRED_HEADSET",
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES to "WIRED_HEADPHONES",
        AudioDeviceInfo.TYPE_LINE_ANALOG to "LINE_ANALOG",
        AudioDeviceInfo.TYPE_LINE_DIGITAL to "LINE_DIGITAL",
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO to "BLUETOOTH_SCO",
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP to "BLUETOOTH_A2DP",
        AudioDeviceInfo.TYPE_HDMI to "HDMI",
        AudioDeviceInfo.TYPE_HDMI_ARC to "HDMI_ARC",
        AudioDeviceInfo.TYPE_USB_DEVICE to "USB_DEVICE",
        AudioDeviceInfo.TYPE_USB_ACCESSORY to "USB_ACCESSORY",
        AudioDeviceInfo.TYPE_DOCK to "DOCK",
        AudioDeviceInfo.TYPE_FM to "FM",
        AudioDeviceInfo.TYPE_BUILTIN_MIC to "BUILTIN_MIC",
        AudioDeviceInfo.TYPE_FM_TUNER to "FM_TUNER",
        AudioDeviceInfo.TYPE_TV_TUNER to "TV_TUNER",
        AudioDeviceInfo.TYPE_TELEPHONY to "TELEPHONY",
        AudioDeviceInfo.TYPE_AUX_LINE to "AUX_LINE",
        AudioDeviceInfo.TYPE_IP to "IP",
        AudioDeviceInfo.TYPE_BUS to "BUS",
        AudioDeviceInfo.TYPE_USB_HEADSET to "USB_HEADSET",
        AudioDeviceInfo.TYPE_HEARING_AID to "HEARING_AID",
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE to "BUILTIN_SPEAKER_SAFE",
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX to "REMOTE_SUBMIX",
        AudioDeviceInfo.TYPE_BLE_HEADSET to "BLE_HEADSET",
        AudioDeviceInfo.TYPE_BLE_SPEAKER to "BLE_SPEAKER",
        AudioDeviceInfo.TYPE_HDMI_EARC to "HDMI_EARC",
        AudioDeviceInfo.TYPE_BLE_BROADCAST to "BLE_BROADCAST",
    )
}

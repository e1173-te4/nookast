package ru.e1173.nookast.player

/**
 * What the player is doing, for parts of the app that must not disturb it (the updater, user
 * 2.11.2026: install only when nothing plays and no car is connected). Written by [PlaybackService].
 */
internal object PlaybackActivity {
    @Volatile
    var playing: Boolean = false
        internal set(value) {
            if (field && !value) pausedAtElapsedMs = android.os.SystemClock.elapsedRealtime()
            field = value
        }

    /** When playback last stopped (elapsed realtime), or 0: a fresh pause may be resumed any moment. */
    @Volatile
    var pausedAtElapsedMs: Long = 0L
        private set

    /** Car controllers (Android Auto and head units) connected to the media session right now. */
    private val carControllers = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    val carConnected: Boolean get() = carControllers.isNotEmpty()

    internal fun controllerConnected(key: String, packageName: String) {
        if (CAR_PACKAGES.any(packageName::startsWith)) carControllers += key
    }

    internal fun controllerDisconnected(key: String) {
        carControllers -= key
    }

    private val CAR_PACKAGES = listOf(
        "com.google.android.projection.gearhead",
        "com.google.android.apps.automotive",
        "com.android.car",
        "com.desaysv",
    )
}

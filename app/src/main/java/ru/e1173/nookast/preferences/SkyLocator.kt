package ru.e1173.nookast.preferences

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * The rough place for the sky (user, 2.11.2026): read from the device only with the location
 * permission, through the platform's own LocationManager (no Google services). A recent known fix is
 * used as it is; otherwise each source is asked for a fresh one in turn, for a while each.
 */
internal object SkyLocator {
    /** Android offers precise or approximate; either will do (precise lets GPS answer where no network
     * location service exists, as on phones without Google services), and the place is rounded anyway. */
    val PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    /** How old a known fix may be and still stand for where the user is. */
    private const val RECENT_FIX_MS = 6 * 60 * 60 * 1_000L
    private const val FRESH_FIX_TIMEOUT_MS = 15_000L

    fun hasPermission(context: Context): Boolean = PERMISSIONS.any {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun locate(context: Context, nowMs: Long = System.currentTimeMillis()): SkyPlace? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(LocationManager::class.java) ?: return null
        val providers = runCatching { manager.getProviders(true) }.getOrDefault(emptyList())
        val known = providers
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull(Location::getTime)
        if (known != null && nowMs - known.time < RECENT_FIX_MS) return known.toPlace(nowMs)
        // Each source in turn: the fused and network ones need a location service, GPS needs the sky.
        val candidates = listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) LocationManager.FUSED_PROVIDER else null,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.GPS_PROVIDER,
        ).filter { it in providers }
        for (provider in candidates) {
            val fresh = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MS) { manager.currentFix(context, provider) }
            if (fresh != null) return fresh.toPlace(nowMs)
        }
        return known?.toPlace(nowMs)
    }

    @SuppressLint("MissingPermission")
    private suspend fun LocationManager.currentFix(context: Context, provider: String): Location? =
        suspendCancellableCoroutine { continuation ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val signal = android.os.CancellationSignal()
                continuation.invokeOnCancellation { signal.cancel() }
                getCurrentLocation(provider, signal, ContextCompat.getMainExecutor(context)) { location ->
                    if (continuation.isActive) continuation.resume(location)
                }
            } else {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        removeUpdates(this)
                        if (continuation.isActive) continuation.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) = Unit
                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                }
                continuation.invokeOnCancellation { removeUpdates(listener) }
                requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
            }
        }

    private fun Location.toPlace(nowMs: Long) = SkyPlace.rough(latitude, longitude, nowMs)

    /** How often the automatic mode looks again while the app is open. */
    const val AUTO_REFRESH_MS = 6 * 60 * 60 * 1_000L
}

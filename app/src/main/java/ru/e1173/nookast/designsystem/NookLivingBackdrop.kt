package ru.e1173.nookast.designsystem

import android.os.Build
import android.os.PowerManager
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The living cover backdrop of the player (design audit 5.2): the clock of the drift, in seconds.
 * It runs only while [active] (playing) and the screen is resumed, never with «remove animations»
 * or the power saver, and slows to a stop instead of freezing at once.
 */
@Composable
fun rememberBackdropDrift(active: Boolean): State<Float> {
    val reduced = rememberReducedMotion()
    val powerSave = rememberPowerSaveMode()
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val run = active && !reduced && !powerSave && lifecycle.isAtLeast(Lifecycle.State.RESUMED)
    val speed by animateFloatAsState(if (run) 1f else 0f, tween(DRIFT_EASE_MS, easing = FastOutSlowInEasing), label = "driftSpeed")
    val currentSpeed by rememberUpdatedState(speed)
    val time = remember { mutableFloatStateOf(0f) }
    val moving = speed > 0f
    LaunchedEffect(moving) {
        if (!moving) return@LaunchedEffect
        // The drift is slow: about 30 redraws a second are plenty (review of audit 5: battery).
        var last = 0L
        var pending = 0f
        var sinceWrite = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) {
                    pending += (now - last) / NANOS_PER_SECOND * currentSpeed
                    sinceWrite += now - last
                    if (sinceWrite >= DRIFT_FRAME_NANOS) {
                        time.floatValue += pending
                        pending = 0f
                        sinceWrite = 0L
                    }
                }
                last = now
            }
        }
    }
    return time
}

/** Where a backdrop light is at [seconds] of drift: closed loops of 40–60 s, 6 % × 4 % of the size. */
fun DrawScope.driftedCenter(base: Offset, seconds: Float, periodX: Float, periodY: Float): Offset = Offset(
    base.x + cos(2f * PI.toFloat() * seconds / periodX) * size.width * DRIFT_X,
    base.y + sin(2f * PI.toFloat() * seconds / periodY) * size.height * DRIFT_Y,
)

/** A light's radius breathing ±5 % over [period] seconds. */
fun driftedRadius(radius: Float, seconds: Float, period: Float): Float =
    radius * (1f + DRIFT_RADIUS * sin(2f * PI.toFloat() * seconds / period))

/** 2–3 % grain over the backdrop on Android 13+, against banding of the soft gradients. */
fun DrawScope.drawBackdropGrain() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    drawRect(ShaderBrush(GrainShader.shader), alpha = GRAIN_ALPHA)
}

private object GrainShader {
    val shader: android.graphics.Shader by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            android.graphics.RuntimeShader(
                """
                half4 main(float2 p) {
                    float n = fract(sin(dot(floor(p), float2(12.9898, 78.233))) * 43758.5453);
                    return half4(half3(n), 1.0);
                }
                """.trimIndent(),
            )
        } else {
            android.graphics.Shader()
        }
    }
}

private const val DRIFT_EASE_MS = 1200
private const val NANOS_PER_SECOND = 1_000_000_000f
private const val DRIFT_FRAME_NANOS = 33_000_000L
private const val DRIFT_X = 0.06f
private const val DRIFT_Y = 0.04f
private const val DRIFT_RADIUS = 0.05f
private const val GRAIN_ALPHA = 0.025f

/** The power saver, kept current while it is read (review of audit 5: it was read once). */
@Composable
fun rememberPowerSaveMode(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager }
    val state = remember(manager) { androidx.compose.runtime.mutableStateOf(manager?.isPowerSaveMode == true) }
    androidx.compose.runtime.DisposableEffect(context, manager) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: android.content.Context?, intent: android.content.Intent?) {
                state.value = manager?.isPowerSaveMode == true
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            receiver,
            android.content.IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onDispose { context.unregisterReceiver(receiver) }
    }
    return state.value
}

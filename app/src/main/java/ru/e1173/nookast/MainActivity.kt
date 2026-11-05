package ru.e1173.nookast

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.yield
import ru.e1173.nookast.auth.LoginViewModel
import ru.e1173.nookast.app.appGraph
import ru.e1173.nookast.diagnostics.ControlJournal
import ru.e1173.nookast.diagnostics.ControlJournalFormat
import ru.e1173.nookast.diagnostics.DeviceSnapshot
import ru.e1173.nookast.ui.NookastRoot
import ru.e1173.nookast.ui.QueueViewModel
import ru.e1173.nookast.ui.CrashRecoveryScreen
import ru.e1173.nookast.ui.theme.NookastTheme
import ru.e1173.nookast.player.PlaybackViewModel
import ru.e1173.nookast.preferences.AppPreferencesViewModel

class MainActivity : ComponentActivity() {
    private var openNowPlayingRequest by mutableLongStateOf(0L)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // The request survives recreation with the counter the UI compares against (U-07).
        openNowPlayingRequest = savedInstanceState?.getLong(STATE_OPEN_NOW_PLAYING) ?: 0L
        consumeLaunchIntent(intent)
        enableEdgeToEdge()
        val crashStore = CrashReportStore(this)
        val pendingCrashReport = crashStore.read()
        setContent {
            val appPreferencesViewModel: AppPreferencesViewModel = viewModel()
            val displayPreferences by appPreferencesViewModel.state.collectAsStateWithLifecycle()
            NookastTheme(themeMode = displayPreferences.themeMode, palette = displayPreferences.palette) {
                if (pendingCrashReport != null) {
                    CrashRecoveryScreen(
                        report = pendingCrashReport,
                        onRetry = {
                            crashStore.clear()
                            recreate()
                        },
                    )
                    return@NookastTheme
                }
                val configuration = LocalConfiguration.current
                val windowSize = LocalWindowInfo.current.containerSize
                val snapshot = remember(
                    windowSize,
                    configuration.smallestScreenWidthDp,
                    configuration.densityDpi,
                    configuration.fontScale,
                ) {
                    DeviceSnapshot.capture(
                        activity = this,
                        windowWidthPx = windowSize.width,
                        windowHeightPx = windowSize.height,
                    )
                }
                val loginViewModel: LoginViewModel = viewModel()
                val queueViewModel: QueueViewModel = viewModel()
                val playbackViewModel: PlaybackViewModel = viewModel()
                LaunchedEffect(playbackViewModel) {
                    // Let Compose publish the local-first frame before Media3 service startup.
                    withFrameNanos { }
                    yield()
                    playbackViewModel.connect()
                }
                NookastRoot(
                    snapshot = snapshot,
                    viewModel = loginViewModel,
                    queueViewModel = queueViewModel,
                    playbackViewModel = playbackViewModel,
                    displayPreferences = displayPreferences,
                    onThemeModeChanged = appPreferencesViewModel::setThemeMode,
                    onInterfaceProfileChanged = appPreferencesViewModel::setInterfaceProfile,
                    onPodcastSortModeChanged = appPreferencesViewModel::setPodcastSortMode,
                    onPodcastViewModeChanged = appPreferencesViewModel::setPodcastViewMode,
                    onPodcastDescriptionCollapsedChanged =
                        appPreferencesViewModel::setPodcastDescriptionCollapsed,
                    onPodcastHidePlayedChanged = appPreferencesViewModel::setPodcastHidePlayed,
                    openNowPlayingRequest = openNowPlayingRequest,
                )
            }
        }
    }

    // Platform Activity method; androidx.core only re-declares it, so the restriction is a lint
    // false positive for an override that calls super.
    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Printing keys are redacted: they may be characters typed into a text field.
        appGraph.controlJournal.record(ControlJournal.Category.ACTIVITY_KEY) {
            ControlJournalFormat.keyEvent(event, redactText = true)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeLaunchIntent(intent)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(STATE_OPEN_NOW_PLAYING, openNowPlayingRequest)
    }

    /** A one-shot request: the action is cleared once taken, so a recreated activity does not repeat it. */
    private fun consumeLaunchIntent(intent: Intent?) {
        if (intent?.action == ACTION_SHOW_UPDATE) {
            ru.e1173.nookast.update.Updater.requestPrompt()
            intent.action = null
        }
        if (intent?.action == ACTION_OPEN_NOW_PLAYING) {
            openNowPlayingRequest += 1L
            intent.action = null
        }
    }

    companion object {
        const val ACTION_OPEN_NOW_PLAYING = "ru.e1173.nookast.action.OPEN_NOW_PLAYING"
        const val ACTION_SHOW_UPDATE = "ru.e1173.nookast.action.SHOW_UPDATE"
        const val OPEN_NOW_PLAYING_REQUEST_CODE = 41
        private const val STATE_OPEN_NOW_PLAYING = "open_now_playing_request"
    }
}

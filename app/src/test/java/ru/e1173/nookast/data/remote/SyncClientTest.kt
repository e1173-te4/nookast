package ru.e1173.nookast.data.remote

import android.app.Application
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class SyncClientTest {
    @Test
    fun acceptsCurrentPinePodsAcknowledgements() {
        validateSuccessBody("""{"detail":"Listen duration recorded."}""", "detail")
        validateSuccessBody("""{"data":"Episode queued successfully"}""", "data")
        validateSuccessBody("""{"message":"Queue reordered successfully"}""", "message")
    }

    @Test
    fun acceptsLegacyBooleanSuccess() {
        validateSuccessBody("true", "detail")
    }

    @Test
    fun rejectsExplicitFailureSoOutboxCanRetry() {
        assertServerFailure { validateSuccessBody("false", "detail") }
        assertServerFailure {
            validateSuccessBody("""{"success":false,"detail":"not saved"}""", "detail")
        }
    }

    @Test
    fun rejectsMalformedOrWrongEndpointResponse() {
        assertServerFailure { validateSuccessBody("", "detail") }
        assertServerFailure { validateSuccessBody("<html>proxy error</html>", "detail") }
        assertServerFailure { validateSuccessBody("""{"message":"wrong contract"}""", "detail") }
    }

    private fun assertServerFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected SyncRequestException")
        } catch (error: SyncRequestException) {
            assertEquals(SyncErrorCategory.SERVER, error.category)
        }
    }
}

package ru.e1173.nookast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashReportSanitizerTest {
    @Test
    fun `redacts API keys and authorization headers`() {
        val report = """
            api_key=top-secret&next=value
            Api-Key: another-secret
            Authorization: Bearer session-secret
        """.trimIndent()

        val sanitized = sanitizeCrashReport(report)

        assertFalse(sanitized.contains("top-secret"))
        assertFalse(sanitized.contains("another-secret"))
        assertFalse(sanitized.contains("session-secret"))
        assertTrue(sanitized.contains("api_key=<redacted>&next=value"))
        assertTrue(sanitized.contains("Authorization: <redacted>"))
    }

    @Test
    fun `redacts JSON secrets, secret parameters and hashes hosts (X-06)`() {
        val report = """
            body={"rss_key":"rss-secret","retrieved_key":"login-secret","user_id":3}
            GET https://pinepods.example.org/api/data/stream/7?api_key=k1&token=t-secret&user_id=3
            at okhttp3.RealCall.execute(RealCall.kt:1)
        """.trimIndent()

        val sanitized = sanitizeCrashReport(report)

        listOf("rss-secret", "login-secret", "t-secret", "k1", "pinepods.example.org").forEach {
            assertFalse("leaked $it", sanitized.contains(it))
        }
        assertTrue(sanitized.contains("\"user_id\":3"))
        assertTrue(sanitized.contains("https://host-"))
        assertTrue("stack frames stay", sanitized.contains("okhttp3.RealCall.execute(RealCall.kt:1)"))
    }

    @Test
    fun `limits report size`() {
        val sanitized = sanitizeCrashReport("x".repeat(MAX_CRASH_REPORT_CHARS + 100))

        assertEquals(MAX_CRASH_REPORT_CHARS, sanitized.length)
    }
}

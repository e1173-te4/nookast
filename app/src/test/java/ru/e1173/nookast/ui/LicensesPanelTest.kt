package ru.e1173.nookast.ui

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The licences list comes from licensee's artifacts.json (DEC-053). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class LicensesPanelTest {
    @Test
    fun `each library becomes one sorted line with its licence`() {
        val json = """
            [{"groupId":"io.coil-kt","artifactId":"coil","version":"3.0.0","spdxLicenses":[{"identifier":"Apache-2.0"}]},
             {"groupId":"androidx.core","artifactId":"core","version":"1.17.0","spdxLicenses":[{"identifier":"Apache-2.0"}]}]
        """.trimIndent()

        assertEquals(
            listOf("androidx.core:core 1.17.0 · Apache-2.0", "io.coil-kt:coil 3.0.0 · Apache-2.0"),
            componentLines(json),
        )
        assertEquals(emptyList<String>(), componentLines("not json"))
    }
}

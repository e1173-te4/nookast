package ru.e1173.nookast.ui

import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ru.e1173.nookast.designsystem.PressWash

/**
 * The user, 13.11.2026: on a wide screen a settings section stayed highlighted after moving to another
 * one, and tapping a highlighted one turned it off. Only the chosen section is highlighted, a second
 * tap keeps it, and a quick tap leaves no press wash behind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w900dp-h1200dp", application = android.app.Application::class)
class SettingsSidebarSelectionTest {
    @get:Rule
    val compose = createComposeRule()

    private fun title(section: SettingsSection) = ApplicationProvider.getApplicationContext<android.content.Context>().getString(section.titleResource)

    @Test
    fun `only the chosen section is highlighted`() {
        var chosen by mutableStateOf(SettingsSection.PLAYER)
        compose.setContent {
            SettingsSidebar(selected = chosen, touchTarget = 48.dp, onBack = {}, onSectionSelected = { chosen = it })
        }
        val a = title(SettingsSection.APPEARANCE)
        val b = title(SettingsSection.SERVER)
        compose.onNodeWithText(a, useUnmergedTree = true).performClick()
        compose.onNodeWithText(b, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        selectedNode(b).assertIsSelected()
        selectedNode(a).assertIsNotSelected()
        selectedNode(title(SettingsSection.PLAYER)).assertIsNotSelected()
        // A second tap on the highlighted section keeps it.
        compose.onNodeWithText(b, useUnmergedTree = true).performClick()
        compose.waitForIdle()
        selectedNode(b).assertIsSelected()
    }

    @Test
    fun `a quick tap leaves no press wash`() {
        val wash = PressWash()
        val press = PressInteraction.Press(Offset.Zero)
        assertEquals(1f, wash.on(press))
        // Press and release in the same frame: the last target is none.
        assertEquals(0f, wash.on(PressInteraction.Release(press)))
        // Two rows pressed one after another: both released, nothing stays lit.
        val other = PressWash()
        val p1 = PressInteraction.Press(Offset.Zero)
        val p2 = PressInteraction.Press(Offset.Zero)
        other.on(p1)
        other.on(PressInteraction.Release(p1))
        other.on(p2)
        assertEquals(0f, other.on(PressInteraction.Cancel(p2)))
    }

    private fun selectedNode(text: String) = compose.onNodeWithText(text)
}

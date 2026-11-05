package ru.e1173.nookast.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until

const val PACKAGE = "ru.e1173.nookast"
private const val WAIT_MS = 5_000L

/**
 * What a listener does most (plan 5.6), driven through the accessibility tree so it works in either
 * language: the Dashboard, the podcast list, a podcast page and Now Playing. The device must be
 * signed in once by hand; without a session only the sign-in screen is measured.
 */
fun MacrobenchmarkScope.signedIn(): Boolean =
    device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS) &&
        device.findObject(By.descContains("Settings")) != null ||
        device.findObject(By.descContains("Настройки")) != null

fun MacrobenchmarkScope.scrollCurrentList() {
    // Compose replaces nodes as it scrolls: each fling finds the list again and a stale one is skipped.
    for (direction in listOf(Direction.DOWN, Direction.UP)) {
        runCatching {
            val list = device.findObject(By.scrollable(true)) ?: return
            list.setGestureMargin(device.displayWidth / 8)
            list.fling(direction)
        }
        device.waitForIdle()
    }
}

fun MacrobenchmarkScope.openTab(vararg names: String): Boolean {
    val tab = names.firstNotNullOfOrNull { device.findObject(By.text(it)) } ?: return false
    tab.click()
    device.waitForIdle()
    return true
}

fun MacrobenchmarkScope.openFirstPodcast() {
    if (!openTab("Podcasts", "Подкасты")) return
    device.wait(Until.hasObject(By.scrollable(true)), WAIT_MS)
    scrollCurrentList()
    runCatching { device.findObject(By.scrollable(true))?.children?.firstOrNull { it.isClickable }?.click() }
    device.waitForIdle()
    scrollCurrentList()
    openEpisodeCard()
    device.pressBack()
    device.waitForIdle()
}

/** An episode row of the podcast page opens its card (APK-3.19); the card is scrolled and closed. */
fun MacrobenchmarkScope.openEpisodeCard() {
    val opened = runCatching {
        device.findObject(By.scrollable(true))?.children?.lastOrNull { it.isClickable }?.click()
    }.isSuccess
    if (!opened) return
    device.waitForIdle()
    scrollCurrentList()
    device.pressBack()
    device.waitForIdle()
}

/** The mini player opens Now Playing when something has been played on the device before. */
fun MacrobenchmarkScope.openNowPlaying(): Boolean {
    val mini = device.findObject(By.descContains("Now Playing")) ?: device.findObject(By.descContains("Сейчас играет"))
        ?: return false
    mini.click()
    device.wait(Until.hasObject(By.text("Description").pkg(PACKAGE)), WAIT_MS)
    device.pressBack()
    device.waitForIdle()
    return true
}

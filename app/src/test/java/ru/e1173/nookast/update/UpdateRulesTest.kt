package ru.e1173.nookast.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The updater's rules (user, 2.11.2026). The two certificates are made-up test values. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class UpdateRulesTest {
    private val release = """
        {"tag_name":"v1.2.0","draft":false,"prerelease":false,
         "body":"Nookast 1.2.0\n\n- Faster feed.\r\n---\nabc  nookast-1.2.0.apk",
         "assets":[
           {"name":"nookast-1.2.0.apk","browser_download_url":"https://example.com/nookast-1.2.0.apk"},
           {"name":"nookast-1.2.0.apk.sha256","browser_download_url":"https://example.com/nookast-1.2.0.apk.sha256"}
         ]}
    """.trimIndent()

    @Test
    fun `a published release is read with its files and notes`() {
        val info = ReleaseRules.parseLatest(release)!!

        assertEquals(listOf(1, 2, 0), info.version)
        assertEquals("nookast-1.2.0.apk", info.apkName)
        assertEquals("https://example.com/nookast-1.2.0.apk", info.apkUrl)
        assertEquals("https://example.com/nookast-1.2.0.apk.sha256", info.shaUrl)
        assertEquals("Nookast 1.2.0\n\n- Faster feed.", ReleaseRules.notesOf(info.body))
    }

    @Test
    fun `notes come in the reader's language from a marked body`() {
        // The real 1.2.2 notes with the checksum tail the release workflow adds.
        val body = javaClass.classLoader!!.getResource("update/release-notes-1.2.2.md")!!.readText()

        val ru = ReleaseRules.notesOf(body, "ru")
        val en = ReleaseRules.notesOf(body, "en")

        assertTrue(ru.startsWith("Первый публичный выпуск"))
        assertTrue(en.startsWith("First public release"))
        assertFalse(ru.contains("lang:") || ru.contains("nookast-1.2.2.apk") || ru.contains("First public"))
        assertFalse(en.contains("Первый") || en.contains("SHA-256 certificate"))
        assertTrue(ReleaseRules.notesOf(body, "de").startsWith("First public release"))
    }

    @Test
    fun `a saved update is named after the release and recognised as ours`() {
        assertEquals("nookast-1.2.4.apk", SavedUpdate.fileName("v1.2.4"))
        assertEquals("nookast-1.3.0.apk", SavedUpdate.fileName("1.3.0"))
        assertTrue(SavedUpdate.isOurs("nookast-1.2.4.apk"))
        assertFalse(SavedUpdate.isOurs("nookast-.apk"))
        assertFalse(SavedUpdate.isOurs("other-1.2.4.apk"))
        assertFalse(SavedUpdate.isOurs("nookast-1.2.4.apk.sha256"))
        assertEquals("AB:0C:FF", SavedUpdate.fingerprint("ab0cff"))
    }

    @Test
    fun `saving is offered in settings for a known version and in the offer after the other way failed`() {
        assertTrue(SavedUpdate.showInSettings("v1.2.4", downloading = false))
        assertFalse(SavedUpdate.showInSettings(null, downloading = false))
        assertFalse(SavedUpdate.showInSettings("v1.2.4", downloading = true))
        assertTrue(SavedUpdate.showInOffer("v1.2.4", classicFailed = true, downloading = false))
        assertFalse(SavedUpdate.showInOffer("v1.2.4", classicFailed = false, downloading = false))
    }

    @Test
    fun `android 11 and a failed session take the classic installer`() {
        assertEquals(InstallPath.CLASSIC, InstallPaths.choose(sdkInt = 30, sessionFailed = false))
        assertEquals(InstallPath.CLASSIC, InstallPaths.choose(sdkInt = 26, sessionFailed = false))
        assertEquals(InstallPath.SESSION, InstallPaths.choose(sdkInt = 31, sessionFailed = false))
        assertEquals(InstallPath.CLASSIC, InstallPaths.choose(sdkInt = 37, sessionFailed = true))
    }

    @Test
    fun `a permission screen that never took the foreground counts as not shown`() {
        assertTrue(InstallPaths.permissionScreenOpened(requestedAtMs = 1_000, pausedAtMs = 1_300))
        assertFalse(InstallPaths.permissionScreenOpened(requestedAtMs = 1_000, pausedAtMs = null))
        assertFalse(InstallPaths.permissionScreenOpened(requestedAtMs = 1_000, pausedAtMs = 2_500))
        assertFalse(InstallPaths.permissionScreenOpened(requestedAtMs = 1_000, pausedAtMs = 900))
    }

    @Test
    fun `the classic installer answer reads as installed, cancelled or failed`() {
        assertEquals(ClassicOutcome.INSTALLED, InstallPaths.classicOutcome(-1))
        assertEquals(ClassicOutcome.CANCELLED, InstallPaths.classicOutcome(0))
        assertEquals(ClassicOutcome.FAILED, InstallPaths.classicOutcome(1))
    }

    @Test
    fun `check now only offers and the background downloads quietly on unmetered networks`() {
        assertEquals(CheckFollowUp.OFFER, UpdateFlow.afterCheck(manual = true, mode = UpdateMode.AUTOMATIC, metered = false))
        assertEquals(CheckFollowUp.OFFER, UpdateFlow.afterCheck(manual = true, mode = UpdateMode.OFF, metered = true))
        assertEquals(CheckFollowUp.DOWNLOAD_QUIETLY, UpdateFlow.afterCheck(manual = false, mode = UpdateMode.AUTOMATIC, metered = false))
        assertEquals(CheckFollowUp.DOWNLOAD_QUIETLY, UpdateFlow.afterCheck(manual = false, mode = UpdateMode.NOTIFY, metered = false))
        assertEquals(CheckFollowUp.NOTHING, UpdateFlow.afterCheck(manual = false, mode = UpdateMode.AUTOMATIC, metered = true))
        assertEquals(CheckFollowUp.NOTHING, UpdateFlow.afterCheck(manual = false, mode = UpdateMode.OFF, metered = false))
    }

    @Test
    fun `the offer installs a downloaded file and warns about mobile data and playback`() {
        val fresh = UpdateFlow.offer("v1.2.4", 4_400_000, downloaded = false, metered = true, playing = true)!!
        assertEquals(OfferAction.DOWNLOAD_AND_INSTALL, fresh.action)
        assertTrue(fresh.metered)
        assertTrue(fresh.stopsPlayback)

        val ready = UpdateFlow.offer("v1.2.4", 4_400_000, downloaded = true, metered = true, playing = false)!!
        assertEquals(OfferAction.INSTALL, ready.action)
        assertFalse(ready.metered)
        assertFalse(ready.stopsPlayback)
        assertNull(UpdateFlow.offer(null, 0, downloaded = false, metered = false, playing = false))
    }

    @Test
    fun `the first public release reads as GitHub serves it`() {
        // releases/latest of e1173-te4/nookast as published on 5.11.2026.
        val json = javaClass.classLoader!!.getResource("update/releases-latest-1.2.3.json")!!.readText()

        val info = ReleaseRules.parseLatest(json)!!

        assertEquals("v1.2.3", info.tag)
        assertEquals(listOf(1, 2, 3), info.version)
        assertEquals("nookast-1.2.3.apk", info.apkName)
        assertEquals(4_418_174L, info.apkSize)
        assertTrue(info.apkUrl.startsWith("https://github.com/e1173-te4/nookast/releases/download/v1.2.3/"))
        assertTrue(info.shaUrl.endsWith("nookast-1.2.3.apk.sha256"))
        assertTrue(ReleaseRules.notesOf(info.body, "ru").isNotBlank())
        assertTrue(ReleaseRules.notesOf(info.body, "en").isNotBlank())
        assertFalse(ReleaseRules.notesOf(info.body, "en").contains("nookast-1.2.3.apk"))
    }

    @Test
    fun `a missing language falls back to the other and an unmarked body to its head`() {
        assertEquals("Только по-русски.", ReleaseRules.notesOf("<!-- lang:ru -->\nТолько по-русски.\n---\nabc  x.apk", "en"))
        assertEquals("Old notes.", ReleaseRules.notesOf("Old notes.\n---\nabc  x.apk", "ru"))
    }

    @Test
    fun `a silent install needs Android 12 and the app as its own installer`() {
        val own = "ru.e1173.nookast"
        assertTrue(InstallPolicy.silentInstallPossible(31, own, own, null))
        assertTrue(InstallPolicy.silentInstallPossible(34, own, own, own))
        assertFalse(InstallPolicy.silentInstallPossible(30, own, own, null))
        assertFalse(InstallPolicy.silentInstallPossible(34, own, "org.telegram.messenger", null))
        assertFalse(InstallPolicy.silentInstallPossible(34, own, own, "com.android.vending"))
        assertFalse(InstallPolicy.silentInstallPossible(33, own, null, null))
    }

    @Test
    fun `an install Android would confirm waits for the user and a fresh pause or a lit screen wait too`() {
        val idle = InstallConditions(playing = false, appOnScreen = false, carConnected = false, downloadsRunning = false)

        assertEquals(InstallDecision.NEEDS_CONFIRMATION, InstallPolicy.decide(UpdateMode.AUTOMATIC, idle.copy(silentInstall = false)))
        assertEquals(InstallDecision.LATER, InstallPolicy.decide(UpdateMode.AUTOMATIC, idle.copy(screenOn = true)))
        assertEquals(InstallDecision.LATER, InstallPolicy.decide(UpdateMode.AUTOMATIC, idle.copy(recentlyPaused = true)))
        assertEquals(InstallDecision.NOTIFY, InstallPolicy.decide(UpdateMode.NOTIFY, idle.copy(silentInstall = false)))
    }

    @Test
    fun `a refused release is remembered by tag and checksum`() {
        val stored = RejectedReleases.key("v1.3.0", "ab")
        assertTrue(RejectedReleases.isRejected(stored, "v1.3.0", "ab"))
        assertFalse(RejectedReleases.isRejected(stored, "v1.3.0", "cd"))
        assertFalse(RejectedReleases.isRejected(null, "v1.3.0", "ab"))
    }

    @Test
    fun `the apk size is read from the release`() {
        val sized = release.replace("\"name\":\"nookast-1.2.0.apk\",", "\"name\":\"nookast-1.2.0.apk\",\"size\":25000000,")
        assertEquals(25_000_000L, ReleaseRules.parseLatest(sized)!!.apkSize)
    }

    @Test
    fun `drafts, pre-releases, rc tags and releases without the checksum are not offered`() {
        assertNull(ReleaseRules.parseLatest(release.replace("\"draft\":false", "\"draft\":true")))
        assertNull(ReleaseRules.parseLatest(release.replace("\"prerelease\":false", "\"prerelease\":true")))
        assertNull(ReleaseRules.parseLatest(release.replace("v1.2.0", "v1.2.0-rc1")))
        assertNull(ReleaseRules.parseLatest(release.replace("nookast-1.2.0.apk.sha256", "other.txt")))
        assertNull(ReleaseRules.parseLatest("not json"))
        assertNull(ReleaseRules.versionOfTag("v1.2.0-rc1"))
    }

    @Test
    fun `versions compare by component`() {
        assertTrue(ReleaseRules.isNewer(listOf(1, 2, 0), ReleaseRules.versionOfName("1.1.5")!!))
        assertTrue(ReleaseRules.isNewer(listOf(1, 10), listOf(1, 9, 9)))
        assertFalse(ReleaseRules.isNewer(listOf(1, 2), listOf(1, 2, 0)))
        assertFalse(ReleaseRules.isNewer(listOf(1, 1, 5), ReleaseRules.versionOfName("1.1.5-debug")!!))
    }

    @Test
    fun `the checksum line must name the file`() {
        val hex = "a".repeat(64)
        assertEquals(hex, ReleaseRules.parseSha256("$hex  nookast-1.2.0.apk\n", "nookast-1.2.0.apk"))
        assertEquals(hex, ReleaseRules.parseSha256("${hex.uppercase()} *nookast-1.2.0.apk", "nookast-1.2.0.apk"))
        assertNull(ReleaseRules.parseSha256("$hex  other.apk", "nookast-1.2.0.apk"))
        assertNull(ReleaseRules.parseSha256("xyz  nookast-1.2.0.apk", "nookast-1.2.0.apk"))
    }

    @Test
    fun `only a newer build of this app with its own certificate may replace it`() {
        val ours = setOf("1111111111111111111111111111111111111111111111111111111111111111")
        val foreign = setOf("2222222222222222222222222222222222222222222222222222222222222222")
        val installed = ApkFacts("ru.e1173.nookast", 64, ours)

        assertNull(ApkRules.check(ApkFacts("ru.e1173.nookast", 65, ours), installed))
        assertEquals(ApkRejection.FOREIGN_SIGNATURE, ApkRules.check(ApkFacts("ru.e1173.nookast", 65, foreign), installed))
        assertEquals(ApkRejection.WRONG_PACKAGE, ApkRules.check(ApkFacts("ru.example.other", 65, ours), installed))
        assertEquals(ApkRejection.NOT_NEWER, ApkRules.check(ApkFacts("ru.e1173.nookast", 64, ours), installed))
        assertEquals(ApkRejection.UNREADABLE, ApkRules.check(null, installed))
    }

    @Test
    fun `automatic install waits for playback, the screen, the car and downloads`() {
        val idle = InstallConditions(playing = false, appOnScreen = false, carConnected = false, downloadsRunning = false)

        assertEquals(InstallDecision.INSTALL_NOW, InstallPolicy.decide(UpdateMode.AUTOMATIC, idle))
        assertEquals(InstallDecision.LATER, InstallPolicy.decide(UpdateMode.AUTOMATIC, idle.copy(playing = true)))
        assertEquals(InstallDecision.LATER, InstallPolicy.decide(UpdateMode.AUTOMATIC, idle.copy(appOnScreen = true)))
        assertEquals(InstallDecision.LATER, InstallPolicy.decide(UpdateMode.AUTOMATIC, idle.copy(carConnected = true)))
        assertEquals(InstallDecision.LATER, InstallPolicy.decide(UpdateMode.AUTOMATIC, idle.copy(downloadsRunning = true)))
        assertEquals(InstallDecision.NOTIFY, InstallPolicy.decide(UpdateMode.NOTIFY, idle))
        assertEquals(InstallDecision.NOTHING, InstallPolicy.decide(UpdateMode.OFF, idle))
    }
}

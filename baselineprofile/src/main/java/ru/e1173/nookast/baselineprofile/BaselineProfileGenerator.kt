package ru.e1173.nookast.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** `gradlew :app:generateBaselineProfile` with the emulator running and signed in (plan 5.6). */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = PACKAGE, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        if (signedIn()) {
            scrollCurrentList()
            openFirstPodcast()
            openNowPlaying()
        }
    }
}

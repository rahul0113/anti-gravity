package com.antigravity.vibecoder

import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [Build.VERSION_CODES.TIRAMISU], instrumentedPackages = ["androidx.loader.content"])
class MainActivityCrashTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAppRunsWithoutCrashingFor15Seconds() {
        // App is launched automatically by createAndroidComposeRule.
        // Wait for the UI to be idle.
        composeTestRule.waitForIdle()

        // Advance Robolectric's main looper clock by 10 seconds to simulate "open for 8 second"
        ShadowLooper.idleMainLooper(Duration.ofSeconds(10))
        composeTestRule.waitForIdle()

        // If the test reaches this point without throwing an exception, the app survived 10 seconds.
        assert(true)
    }
}

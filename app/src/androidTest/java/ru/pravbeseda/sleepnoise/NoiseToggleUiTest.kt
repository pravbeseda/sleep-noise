package ru.pravbeseda.sleepnoise

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.content.edit
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.pravbeseda.sleepnoise.media.NOISE_LAB_CANDIDATES
import ru.pravbeseda.sleepnoise.media.SHIPPING_NOISES
import ru.pravbeseda.sleepnoise.ui.NoiseControlView

/**
 * Every noise on the screen carries the same controls. Its speaker toggle silences it without touching
 * its level — the slider keeps its position, the stored volume keeps its value, and only the toggle's
 * own preference changes — and the slider drives the toggle the other way round: a level the user sets
 * switches the noise on, a level dragged to zero switches it off, a level of zero already stored reads
 * as off when the row is bound, and switching a silent noise on raises it to the quietest audible level.
 *
 * Nothing here presses play, so no foreground service and no audio outlives the test. What the
 * service makes of a stored toggle is therefore not covered — see the PR description.
 */
@RunWith(AndroidJUnit4::class)
class NoiseToggleUiTest {
    private val preferences = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    @Before
    fun startFromAnUntouchedInstall() = forgetNoiseSettings()

    @After
    fun leaveAnUntouchedInstall() = forgetNoiseSettings()

    /** The test writes real preferences on the device, so it takes them back out again. */
    private fun forgetNoiseSettings() = preferences.edit(commit = true) {
        SHIPPING_NOISES.forEach {
            remove(it.volumeKey)
            remove(it.enabledKey)
        }
        NOISE_LAB_CANDIDATES.forEach {
            remove(it.preferenceKey)
            remove(it.enabledPreferenceKey)
        }
    }

    /**
     * An untouched install has every `*Enabled` key set, but only brown starts at a level above zero:
     * every other noise, shipping or lab, sits at 0 %. A sounding speaker over a silent slider would be
     * saying something untrue, so the level has the last word on what the toggle shows.
     */
    @Test
    fun anUntouchedInstallShowsOnlyTheNoisesThatCanBeHeard() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.eachNoiseControl().forEach { control ->
                    val level = control.slider().progress
                    if (level > 0) {
                        assertTrue("the toggle of a row at $level %", control.noiseToggle().isChecked)
                        assertEquals("the controls' alpha at $level %", 1f, control.controls().alpha, 0f)
                    } else {
                        assertFalse("the toggle of a row at $level %", control.noiseToggle().isChecked)
                        assertTrue("the controls of a silent noise are dimmed", control.controls().alpha < 1f)
                    }
                }
            }
        }
    }

    /** A level the user sets is what they want to hear, so it brings a switched-off noise back on. */
    @Test
    fun settingALevelSwitchesTheNoiseOn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pink = activity.noiseControl(PINK_NOISE_VOLUME)
                assertFalse("pink starts silent on an untouched install", pink.noiseToggle().isChecked)

                pink.setSliderByUser(CHOSEN_PROGRESS)

                assertTrue("the toggle after a level was set", pink.noiseToggle().isChecked)
                assertTrue("the toggle stored as on", preferences.getBoolean(PINK_NOISE_ENABLED, false))
                assertEquals("the controls' alpha", 1f, pink.controls().alpha, 0f)
            }
        }
    }

    /**
     * The other direction: a level dragged to zero is a noise switched off, icon and all. Brown is
     * the noise under test here because it is the one an untouched install starts at a level above
     * zero, so the drag has somewhere to come from.
     */
    @Test
    fun draggingTheLevelToZeroSwitchesTheNoiseOff() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val brown = activity.noiseControl(BROWN_NOISE_VOLUME)

                brown.setSliderByUser(0)

                assertFalse("the toggle after the level reached zero", brown.noiseToggle().isChecked)
                assertFalse("the toggle stored as off", preferences.getBoolean(BROWN_NOISE_ENABLED, true))
                assertTrue("the controls of a switched-off noise are dimmed", brown.controls().alpha < 1f)
            }
        }
    }

    /**
     * The counterpart of a level dragged to zero: switching a silent noise on has to make it audible,
     * or the speaker would claim a sound the slider says is not there.
     */
    @Test
    fun switchingASilentNoiseOnGivesItTheQuietestAudibleLevel() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pink = activity.noiseControl(PINK_NOISE_VOLUME)
                assertFalse("pink starts silent on an untouched install", pink.noiseToggle().isChecked)

                pink.noiseToggle().isChecked = true

                assertEquals("pink's level after it was switched on", MIN_AUDIBLE_PROGRESS, pink.slider().progress)
                assertEquals(
                    "pink's stored level after it was switched on",
                    MIN_AUDIBLE_PROGRESS / PERCENT_SCALE,
                    preferences.getFloat(PINK_NOISE_VOLUME, Float.NaN),
                    0f,
                )
                assertEquals("the controls' alpha", 1f, pink.controls().alpha, 0f)
            }
        }
    }

    /** Brown again, as the noise an untouched install starts with switched on. */
    @Test
    fun switchingANoiseOffKeepsItsLevelAndDimsItsControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val brown = activity.noiseControl(BROWN_NOISE_VOLUME)
                brown.slider().progress = CHOSEN_PROGRESS
                brown.noiseToggle().isChecked = false

                assertEquals(
                    "the level stored while the noise is off",
                    CHOSEN_PROGRESS / PERCENT_SCALE,
                    preferences.getFloat(BROWN_NOISE_VOLUME, Float.NaN),
                    0f,
                )
                assertEquals("the slider left where the user put it", CHOSEN_PROGRESS, brown.slider().progress)
                assertFalse("the toggle stored as off", preferences.getBoolean(BROWN_NOISE_ENABLED, true))
                assertTrue("the controls of a switched-off noise are dimmed", brown.controls().alpha < 1f)
            }
        }
    }

    /**
     * A theme or a language change goes through `recreate()`, which saves and restores the view
     * hierarchy. Every row inflates the same layout, so its children share their ids: left to the
     * default dispatch, Android would collapse the four sliders into one entry keyed by
     * `noiseSlider` and hand the last row's value back to all of them — over the levels the toggle
     * exists to preserve, and into each noise's preferences, since the listeners are already on.
     */
    @Test
    fun recreatingTheScreenLeavesEveryNoiseWithItsOwnSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val pink = activity.noiseControl(PINK_NOISE_VOLUME)
                // The level first, as the user would set it, so that switching the noise off after
                // it is a state the row actually stores rather than the one it already had.
                pink.setSliderByUser(CHOSEN_PROGRESS)
                pink.noiseToggle().isChecked = false
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val pink = activity.noiseControl(PINK_NOISE_VOLUME)
                assertEquals("pink's slider after a recreate", CHOSEN_PROGRESS, pink.slider().progress)
                assertFalse("pink's toggle after a recreate", pink.noiseToggle().isChecked)
                assertEquals(
                    "pink's stored level after a recreate",
                    CHOSEN_PROGRESS / PERCENT_SCALE,
                    preferences.getFloat(PINK_NOISE_VOLUME, Float.NaN),
                    0f,
                )

                val brown = activity.noiseControl(BROWN_NOISE_VOLUME)
                val brownProgress = (DEFAULT_BROWN_NOISE_VOLUME * PERCENT_SCALE).toInt()
                assertEquals("brown's slider after a recreate", brownProgress, brown.slider().progress)
                assertTrue("brown's toggle after a recreate", brown.noiseToggle().isChecked)
            }
        }
    }

    /** One noise's toggle gates that noise only: the other keeps both its state and its level. */
    @Test
    fun switchingOneNoiseOffLeavesTheOtherAlone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.noiseControl(BROWN_NOISE_VOLUME).noiseToggle().isChecked = false

                val pink = activity.noiseControl(PINK_NOISE_VOLUME)
                assertEquals("pink's level", 0, pink.slider().progress)
                assertFalse("pink's own preference was written", preferences.contains(PINK_NOISE_ENABLED))
                assertFalse("pink's level was written", preferences.contains(PINK_NOISE_VOLUME))
            }
        }
    }

    /**
     * The rows carry no ids — they are built from the registries — so a row is asked for by the key its
     * noise stores its level under, which is what the screen files them by.
     */
    private fun MainActivity.noiseControl(volumeKey: String): NoiseControlView = noiseRows.getValue(volumeKey)

    /** Every noise on the screen: the ones the app ships, and every lab experiment the build has switched on. */
    private fun MainActivity.eachNoiseControl(): List<NoiseControlView> = noiseRows.values.toList()

    // Views inside a NoiseControlView share their ids across instances, so they are looked up on the row itself.
    private fun NoiseControlView.noiseToggle(): AppCompatCheckBox = findViewById(R.id.noiseToggle)

    private fun NoiseControlView.controls(): View = findViewById(R.id.noiseControls)

    private fun NoiseControlView.slider(): SeekBar = findViewById(R.id.noiseSlider)

    /**
     * A level as the user sets one. Assigning to `progress` reports `fromUser = false` and so leaves
     * the toggle alone by design; the accessibility action a screen reader uses goes through the
     * widget's own path and arrives with the flag set, which a swipe would too — without landing on
     * a level these assertions can name.
     */
    private fun NoiseControlView.setSliderByUser(progress: Int) {
        val arguments = Bundle().apply {
            putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, progress.toFloat())
        }
        val accepted = slider().performAccessibilityAction(
            AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.id,
            arguments,
        )
        assertTrue("the seekbar accepted a user-set level", accepted)
    }

    private companion object {
        const val CHOSEN_PROGRESS = 37
        const val MIN_AUDIBLE_PROGRESS = 1
        const val PERCENT_SCALE = 100f
    }
}

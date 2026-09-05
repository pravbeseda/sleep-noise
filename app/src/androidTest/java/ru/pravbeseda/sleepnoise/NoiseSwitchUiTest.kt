package ru.pravbeseda.sleepnoise

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import androidx.appcompat.widget.SwitchCompat
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
import ru.pravbeseda.sleepnoise.ui.NoiseControlView

/**
 * Every noise on the screen carries the same controls, and its switch turns it off without touching
 * its level: the slider keeps its position, the stored volume keeps its value, and only the switch's
 * own preference changes.
 *
 * Nothing here presses play, so no foreground service and no audio outlives the test. What the
 * service makes of a stored switch is therefore not covered — see the PR description.
 */
@RunWith(AndroidJUnit4::class)
class NoiseSwitchUiTest {
    private val preferences = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    @Before
    fun startFromAnUntouchedInstall() = forgetNoiseSettings()

    @After
    fun leaveAnUntouchedInstall() = forgetNoiseSettings()

    /** The test writes real preferences on the device, so it takes them back out again. */
    private fun forgetNoiseSettings() = preferences.edit(commit = true) {
        remove(WHITE_NOISE_ENABLED)
        remove(BROWN_NOISE_ENABLED)
        remove(WHITE_NOISE_VOLUME)
        remove(BROWN_NOISE_VOLUME)
        NOISE_LAB_CANDIDATES.forEach {
            remove(it.preferenceKey)
            remove(it.enabledPreferenceKey)
        }
    }

    @Test
    fun anUntouchedInstallHasBothNoisesSwitchedOn() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.eachNoiseControl().forEach { control ->
                    assertTrue("a switch on an untouched install", control.noiseSwitch().isChecked)
                    assertEquals("the controls' alpha", 1f, control.controls().alpha, 0f)
                }
            }
        }
    }

    @Test
    fun switchingANoiseOffKeepsItsLevelAndDimsItsControls() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val white = activity.noiseControl(R.id.whiteNoiseControl)
                white.slider().progress = CHOSEN_PROGRESS
                white.noiseSwitch().isChecked = false

                assertEquals(
                    "the level stored while the noise is off",
                    CHOSEN_PROGRESS / PERCENT_SCALE,
                    preferences.getFloat(WHITE_NOISE_VOLUME, Float.NaN),
                    0f,
                )
                assertEquals("the slider left where the user put it", CHOSEN_PROGRESS, white.slider().progress)
                assertFalse("the switch stored as off", preferences.getBoolean(WHITE_NOISE_ENABLED, true))
                assertTrue("the controls of a switched-off noise are dimmed", white.controls().alpha < 1f)
            }
        }
    }

    /**
     * A theme or a language change goes through `recreate()`, which saves and restores the view
     * hierarchy. Every row inflates the same layout, so its children share their ids: left to the
     * default dispatch, Android would collapse the four sliders into one entry keyed by
     * `noiseSlider` and hand the last row's value back to all of them — over the levels the switch
     * exists to preserve, and into each noise's preferences, since the listeners are already on.
     */
    @Test
    fun recreatingTheScreenLeavesEveryNoiseWithItsOwnSettings() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val white = activity.noiseControl(R.id.whiteNoiseControl)
                white.slider().progress = CHOSEN_PROGRESS
                white.noiseSwitch().isChecked = false
            }

            scenario.recreate()

            scenario.onActivity { activity ->
                val white = activity.noiseControl(R.id.whiteNoiseControl)
                assertEquals("white's slider after a recreate", CHOSEN_PROGRESS, white.slider().progress)
                assertFalse("white's switch after a recreate", white.noiseSwitch().isChecked)
                assertEquals(
                    "white's stored level after a recreate",
                    CHOSEN_PROGRESS / PERCENT_SCALE,
                    preferences.getFloat(WHITE_NOISE_VOLUME, Float.NaN),
                    0f,
                )

                val brown = activity.noiseControl(R.id.brownNoiseControl)
                val brownProgress = (DEFAULT_BROWN_NOISE_VOLUME * PERCENT_SCALE).toInt()
                assertEquals("brown's slider after a recreate", brownProgress, brown.slider().progress)
                assertTrue("brown's switch after a recreate", brown.noiseSwitch().isChecked)
            }
        }
    }

    /** One noise's switch gates that noise only: the other keeps both its state and its level. */
    @Test
    fun switchingOneNoiseOffLeavesTheOtherAlone() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.noiseControl(R.id.whiteNoiseControl).noiseSwitch().isChecked = false

                val brown = activity.noiseControl(R.id.brownNoiseControl)
                assertTrue("the brown noise switch", brown.noiseSwitch().isChecked)
                assertEquals("brown controls alpha", 1f, brown.controls().alpha, 0f)
                assertFalse("brown's own preference was written", preferences.contains(BROWN_NOISE_ENABLED))
            }
        }
    }

    private fun MainActivity.noiseControl(id: Int): NoiseControlView = findViewById(id)

    /** Both shipping noises, and every lab experiment the build has switched on. */
    private fun MainActivity.eachNoiseControl(): List<NoiseControlView> =
        listOf(noiseControl(R.id.whiteNoiseControl), noiseControl(R.id.brownNoiseControl)) + labNoiseControls()

    /** Whatever the lab put on the screen: none of it with the lab switched off. */
    private fun MainActivity.labNoiseControls(): List<NoiseControlView> {
        val container: LinearLayout = findViewById(R.id.noiseLabContainer)
        return (0 until container.childCount).map { container.getChildAt(it) as NoiseControlView }
    }

    // Views inside a NoiseControlView share their ids across instances, so they are looked up on the row itself.
    private fun NoiseControlView.noiseSwitch(): SwitchCompat = findViewById(R.id.noiseSwitch)

    private fun NoiseControlView.controls(): View = findViewById(R.id.noiseControls)

    private fun NoiseControlView.slider(): SeekBar = findViewById(R.id.noiseSlider)

    private companion object {
        const val CHOSEN_PROGRESS = 37
        const val PERCENT_SCALE = 100f
    }
}

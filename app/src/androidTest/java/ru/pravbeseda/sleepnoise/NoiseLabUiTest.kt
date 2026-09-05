package ru.pravbeseda.sleepnoise

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
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
import ru.pravbeseda.sleepnoise.media.NOISE_LAB_ENABLED
import ru.pravbeseda.sleepnoise.ui.NoiseControlView

/**
 * The lab screen is what the registry says it is: one [NoiseControlView] per entry of
 * [NOISE_LAB_CANDIDATES] — the same row the shipping noises get, switch included — each persisting
 * under that entry's own preference key.
 *
 * Both sides of the flag are asserted rather than skipped, so the class keeps its meaning when
 * [NOISE_LAB_ENABLED] is turned off: with the lab away the container is gone, empty, and writes no
 * preference — a stored lab volume with no slider to turn it down is the exact failure the flag has
 * to avoid. A skip would report green on an empty run and say nothing about either.
 *
 * Nothing here presses play, so no foreground service and no audio outlives the test; the binding
 * [MainActivity] makes in `onStart` is torn down with the Activity by [ActivityScenario.close].
 */
@RunWith(AndroidJUnit4::class)
class NoiseLabUiTest {
    private val preferences = InstrumentationRegistry.getInstrumentation()
        .targetContext
        .getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)

    @Before
    fun startFromAnUntouchedInstall() = forgetLabVolumes()

    @After
    fun leaveAnUntouchedInstall() = forgetLabVolumes()

    /** The test writes real preferences on the device, so it takes them back out again. */
    private fun forgetLabVolumes() = preferences.edit(commit = true) {
        NOISE_LAB_CANDIDATES.forEach {
            remove(it.preferenceKey)
            remove(it.enabledPreferenceKey)
        }
    }

    @Test
    fun theContainerHoldsOneLabelledRowPerCandidate() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val container = activity.labContainer()
                if (!NOISE_LAB_ENABLED) {
                    assertEquals("lab container visibility with the lab off", View.GONE, container.visibility)
                    assertEquals("views in the lab container with the lab off", 0, container.childCount)
                    return@onActivity
                }

                assertEquals("lab container visibility", View.VISIBLE, container.visibility)
                assertEquals(
                    "views in the lab container: one row per candidate",
                    NOISE_LAB_CANDIDATES.size,
                    container.childCount,
                )
                NOISE_LAB_CANDIDATES.forEachIndexed { index, candidate ->
                    val row = container.getChildAt(index)
                    assertTrue("the view at $index is not a NoiseControlView", row is NoiseControlView)
                    val text = (row as NoiseControlView).findViewById<TextView>(R.id.noiseLabel).text.toString()
                    assertTrue("the label of ${candidate.preferenceKey} reads \"$text\"", text.startsWith(candidate.label))
                }
            }
        }
    }

    @Test
    fun movingASliderPersistsThatCandidatesOwnVolume() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            if (!NOISE_LAB_ENABLED) {
                NOISE_LAB_CANDIDATES.forEach { candidate ->
                    assertFalse(
                        "${candidate.preferenceKey} was stored with the lab off",
                        preferences.contains(candidate.preferenceKey),
                    )
                }
                return@use
            }

            // A different value per slider, so a shared or a swapped key fails here instead of passing by luck.
            val progressByKey = NOISE_LAB_CANDIDATES
                .mapIndexed { index, candidate -> candidate.preferenceKey to FIRST_PROGRESS + index * PROGRESS_STEP }
                .toMap()

            scenario.onActivity { activity ->
                val container = activity.labContainer()
                NOISE_LAB_CANDIDATES.forEachIndexed { index, candidate ->
                    container.sliderAt(index).progress = progressByKey.getValue(candidate.preferenceKey)
                }
            }

            progressByKey.forEach { (key, progress) ->
                assertEquals(
                    "volume stored under $key",
                    progress / PERCENT_SCALE,
                    preferences.getFloat(key, Float.NaN),
                    0f,
                )
            }
        }
    }

    private fun MainActivity.labContainer(): LinearLayout = findViewById(R.id.noiseLabContainer)

    /** The slider of the candidate at [index], looked up inside that candidate's own row. */
    private fun LinearLayout.sliderAt(index: Int): SeekBar = getChildAt(index).findViewById(R.id.noiseSlider)

    private companion object {
        const val FIRST_PROGRESS = 23
        const val PROGRESS_STEP = 31
        const val PERCENT_SCALE = 100f
    }
}

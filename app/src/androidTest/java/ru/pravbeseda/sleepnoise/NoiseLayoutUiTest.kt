package ru.pravbeseda.sleepnoise

import android.graphics.Rect
import android.view.View
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The screen has two arrangements and one promise. While the window holds the blocks under the noise
 * rows and a row to read, those blocks stay pinned to the bottom and the rows scroll inside what is
 * left; below that height the whole screen scrolls instead. Either way every control can be brought
 * fully into view, and only one of the two scrolls ever moves.
 *
 * Both cases are made here rather than waited for. How many rows there are is the noise lab's to
 * decide, and a test that only passes while some flag adds enough of them asserts the flag rather
 * than the layout; a window shorter than the emulator's is not something a rotation can be asked for
 * at all. That second case is the one that shipped broken — in landscape the rows had the only height
 * that could give way, so they gave way to nothing while the play button went up under the action bar
 * and no part of the screen scrolled.
 */
@RunWith(AndroidJUnit4::class)
class NoiseLayoutUiTest {
    @Test
    fun theBlocksUnderTheRowsStayPutWhenTheRowsOutgrowTheWindow() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.addARowTallerThanTheWindow()
                relayout(activity.window(), activity.windowHeight())

                for ((id, name) in PINNED_BLOCKS) {
                    val view: View = activity.findViewById(id)
                    assertEquals("$name does not stay in the window", view.height, view.visibleHeight())
                }
                assertTrue("the rows do not scroll", activity.scroll(R.id.noiseScroll).canScrollVertically(DOWN))
                assertFalse("the screen scrolls as well as the rows", activity.scroll(R.id.contentScroll).canScrollVertically(DOWN))
                activity.assertEveryControlIsReachable()
            }
        }
    }

    @Test
    fun theWholeScreenScrollsInAWindowTooShortToPinAnything() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                relayout(activity.window(), activity.shortWindowHeight())

                assertTrue("the screen does not scroll", activity.scroll(R.id.contentScroll).canScrollVertically(DOWN))
                assertFalse("the rows scroll as well as the screen", activity.scroll(R.id.noiseScroll).canScrollVertically(DOWN))
                activity.assertEveryControlIsReachable()
            }
        }
    }

    /** Scrolls each control into view in turn: what a user cannot reach this way is not on the screen. */
    private fun MainActivity.assertEveryControlIsReachable() {
        for ((id, name) in CONTROLS) {
            val view: View = findViewById(id)
            view.requestRectangleOnScreen(Rect(0, 0, view.width, view.height), true)
            assertEquals("$name cannot be brought into view", view.height, view.visibleHeight())
        }
    }

    private fun MainActivity.addARowTallerThanTheWindow() {
        val container: LinearLayout = findViewById(R.id.noiseLabContainer)
        container.visibility = View.VISIBLE
        container.addView(Space(this).apply { minimumHeight = windowHeight() * OVERFLOW_FACTOR })
    }

    private fun MainActivity.scroll(id: Int): ScrollView = findViewById(id)

    private fun MainActivity.window(): View = findViewById(android.R.id.content)

    private fun MainActivity.windowHeight(): Int = window().height

    /**
     * A share of the screen is not a window size: at 420dpi a third of a phone leaves less room than
     * one noise row occupies, and a row that cannot fit is one no arrangement can bring into view.
     * SHORT_WINDOW_HEIGHT_DP is a height instead, so every screen gets the same short window.
     */
    private fun MainActivity.shortWindowHeight(): Int = (SHORT_WINDOW_HEIGHT_DP * resources.displayMetrics.density).toInt()

    /**
     * Lays the window out again at the given height, on the spot: a requested layout arrives with the
     * next frame, which is one message later than this test reads, and a height the window never had
     * is not something a rotation on an emulator can be asked for anyway.
     */
    private fun relayout(window: View, height: Int) {
        window.measure(
            MeasureSpec.makeMeasureSpec(window.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY),
        )
        window.layout(window.left, window.top, window.right, window.top + height)
    }

    /** How much of the view the window actually shows, which is 0 once it has been pushed past an edge. */
    private fun View.visibleHeight(): Int {
        val visible = Rect()
        return if (getGlobalVisibleRect(visible)) visible.height() else 0
    }

    private companion object {
        /** Every control a user has to be able to reach, named for the failure message. */
        val CONTROLS =
            listOf(
                R.id.whiteNoiseControl to "the white noise row",
                R.id.pinkNoiseControl to "the pink noise row",
                R.id.brownNoiseControl to "the brown noise row",
                R.id.playButton to "the play button",
                R.id.timerView to "the timer",
            )

        /** What stays at the bottom of a window that has room for it, rows or no rows. */
        val PINNED_BLOCKS =
            listOf(
                R.id.playButton to "the play button",
                R.id.timerView to "the timer",
                R.id.cats to "the picture",
                R.id.version_text to "the version line",
            )

        /** canScrollVertically's direction, which is a sign rather than a constant of its own. */
        const val DOWN = 1

        /** Enough that the rows overflow on any screen the app runs on, however few of them it ships. */
        const val OVERFLOW_FACTOR = 2

        /**
         * The smallest screen dimension Android hands out, and about what a phone in landscape comes
         * to: short enough that the blocks under the rows and a row to read cannot both fit, and tall
         * enough that a control still can.
         */
        const val SHORT_WINDOW_HEIGHT_DP = 320
    }
}

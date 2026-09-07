package ru.pravbeseda.sleepnoise

import android.view.View
import android.view.View.MeasureSpec
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Space
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The noise rows own the height left above the play button and no more: rows that outgrow that region
 * scroll inside it rather than pushing the play button off the bottom of the screen.
 *
 * The overflow is made here rather than waited for — the lab decides how many rows an install shows,
 * and a test that only passes while some flag adds enough of them asserts the flag, not the layout.
 */
@RunWith(AndroidJUnit4::class)
class NoiseLayoutUiTest {
    @Test
    fun rowsThatOutgrowTheScreenScrollAndLeaveThePlayButtonWhereItIs() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                val scroll: ScrollView = activity.findViewById(R.id.noiseScroll)
                val playButton: View = activity.findViewById(R.id.playButton)
                assertTrue(
                    "the pink noise row is not inside the scrolling region",
                    activity.findViewById<View>(R.id.pinkNoiseControl).ancestors().contains(scroll),
                )

                activity.fillPastTheViewport(scroll)

                assertTrue("the rows fit after filling the region twice over", scroll.canScrollVertically(1))
                assertEquals("the play button is off the screen", playButton.height, playButton.visibleHeight())
            }
        }
    }

    /**
     * Adds a row taller than the region itself, then lays the window out again on the spot: a
     * requested layout arrives with the next frame, which is one message later than this test reads.
     */
    private fun MainActivity.fillPastTheViewport(scroll: ScrollView) {
        val container: LinearLayout = findViewById(R.id.noiseLabContainer)
        container.visibility = View.VISIBLE
        container.addView(Space(this).apply { minimumHeight = scroll.height * OVERFLOW_FACTOR })

        val root: View = findViewById(android.R.id.content)
        root.measure(
            MeasureSpec.makeMeasureSpec(root.width, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(root.height, MeasureSpec.EXACTLY),
        )
        root.layout(root.left, root.top, root.right, root.bottom)
    }

    private fun View.ancestors(): Sequence<View> = generateSequence(parent as? View) { it.parent as? View }

    /** How much of the view the window actually shows, which is 0 once it has been pushed past the bottom. */
    private fun View.visibleHeight(): Int {
        val visible = android.graphics.Rect()
        return if (getGlobalVisibleRect(visible)) visible.height() else 0
    }

    private companion object {
        /** Enough that the region overflows on any screen the app runs on, however few rows it ships. */
        const val OVERFLOW_FACTOR = 2
    }
}

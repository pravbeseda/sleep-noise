package ru.pravbeseda.sleepnoise.ui

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import ru.pravbeseda.sleepnoise.R

/**
 * The screen's outer scroll, and the one decision about this screen that a layout file cannot make:
 * whether the blocks under the noise rows are pinned to the bottom of the window or scrolled along
 * with them.
 *
 * While the window holds those blocks and one whole noise row, the column is measured at exactly the
 * window's height — the rows region then takes what the blocks leave and scrolls inside it, so the
 * play button, the picture and the version line stay where they are however many noises the app
 * shows. Below that height the column is measured at its own height instead and this scroll moves
 * the whole screen at once, which is what a landscape phone needs: there, pinning those blocks
 * leaves the rows a strip too thin to read, and the screen shipped with no scroll at all.
 *
 * Only one of the two scrolls can ever move. Pinned, this one is exactly as tall as its content;
 * unpinned, the rows region is exactly as tall as its own. A drag therefore never has two answers.
 */
class BottomPinningScrollView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null) : ScrollView(context, attrs) {
    private var pinning = false

    /** Resolved on the first measure rather than at construction, which is before the column exists. */
    private val rows: View by lazy { findViewById(R.id.noiseScroll) }
    private val oneRow: View by lazy { findViewById(R.id.whiteNoiseControl) }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        pinning = false
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        pinning = thePinnedBlocksAndARowFit(heightMeasureSpec)
        if (pinning) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    /**
     * Hands the column the window's own height while pinning, which is what puts the blocks under
     * the rows at the bottom of it. Unpinned, ScrollView's answer — as tall as you like — is the
     * one wanted, and is left alone.
     */
    override fun measureChildWithMargins(
        child: View,
        parentWidthMeasureSpec: Int,
        widthUsed: Int,
        parentHeightMeasureSpec: Int,
        heightUsed: Int,
    ) {
        if (!pinning) {
            super.measureChildWithMargins(child, parentWidthMeasureSpec, widthUsed, parentHeightMeasureSpec, heightUsed)
            return
        }
        val margins = child.layoutParams as ViewGroup.MarginLayoutParams
        val width =
            getChildMeasureSpec(
                parentWidthMeasureSpec,
                paddingLeft + paddingRight + margins.leftMargin + margins.rightMargin + widthUsed,
                margins.width,
            )
        val height = viewport(parentHeightMeasureSpec) - margins.topMargin - margins.bottomMargin
        child.measure(width, MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY))
    }

    /** Read off the pass that has just measured every block at its own height. */
    private fun thePinnedBlocksAndARowFit(heightMeasureSpec: Int): Boolean {
        val column = getChildAt(0) ?: return false
        val blocksUnderTheRows = column.measuredHeight - rows.measuredHeight
        return viewport(heightMeasureSpec) - blocksUnderTheRows >= oneRow.measuredHeight
    }

    private fun viewport(heightMeasureSpec: Int): Int = MeasureSpec.getSize(heightMeasureSpec) - paddingTop - paddingBottom
}

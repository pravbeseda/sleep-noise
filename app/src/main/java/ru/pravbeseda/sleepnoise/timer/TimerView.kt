package ru.pravbeseda.sleepnoise.timer

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import ru.pravbeseda.sleepnoise.R
import java.util.Locale

class TimerView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private val timerTextView: TextView
    private val timerSeekBar: SeekBar

    /** Called with the minutes the user picks on the seekbar; a value assigned to [minutes] is not reported. */
    var onMinutesChanged: ((Int) -> Unit)? = null

    var minutes: Int
        get() = timerSeekBar.progress * 30
        set(value) {
            timerSeekBar.progress = value / 30
            updateTimerText(timerSeekBar.progress)
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.timer_view, this, true)
        timerTextView = findViewById(R.id.timerTextView)
        timerSeekBar = findViewById(R.id.timerSeekBar)

        updateTimerText(timerSeekBar.progress)

        timerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateTimerText(progress)
                if (fromUser) onMinutesChanged?.invoke(progress * 30)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateTimerText(progress: Int) {
        val totalMinutes = progress * 30
        timerTextView.text = String.format(Locale.getDefault(), "%02d:%02d", totalMinutes / 60, totalMinutes % 60)
    }

    fun showCountdown(remainingMillis: Long) {
        timerTextView.text = SleepTimer.formatRemaining(remainingMillis)
    }

    /** Back from playing, the label trades the countdown for the minutes still on the seekbar. */
    fun setPlayingState(isPlaying: Boolean) {
        timerSeekBar.visibility = if (isPlaying) View.INVISIBLE else View.VISIBLE
        if (!isPlaying) updateTimerText(timerSeekBar.progress)
    }
}

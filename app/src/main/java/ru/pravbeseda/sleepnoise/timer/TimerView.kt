package ru.pravbeseda.sleepnoise.timer

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import ru.pravbeseda.sleepnoise.R

class TimerView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {
    private lateinit var timerPreferences: TimerPreferences
    private val timerTextView: TextView
    private val timerSeekBar: SeekBar

    private var timerValueInMinutes: Int = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.timer_view, this, true)
        timerTextView = findViewById(R.id.timerTextView)
        timerSeekBar = findViewById(R.id.timerSeekBar)

        timerPreferences = TimerPreferences(this.context)
        setPlayingState(false)

        timerSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                updateTimerText(progress)
                timerPreferences.saveTimerValue(progress * 30)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }

    private fun updateTimerText(progress: Int) {
        timerValueInMinutes = progress * 30
        val hours = timerValueInMinutes / 60
        val minutes = timerValueInMinutes % 60
        timerTextView.text = String.format("%02d:%02d", hours, minutes)
    }

    fun showCountdown(time: String) {
        timerTextView.text = time
    }

    fun getTimerValueInMinutes(): Int {
        return timerValueInMinutes
    }

    fun setPlayingState(isPlaying: Boolean) {
        timerSeekBar.visibility = if (isPlaying) View.INVISIBLE else View.VISIBLE
        if (!isPlaying) {
            val savedTimerValue = timerPreferences.getTimerValue()
            setTimerValue(savedTimerValue)
        }
    }

    private fun setTimerValue(minutes: Int) {
        timerValueInMinutes = minutes
        timerSeekBar.progress = minutes / 30
        updateTimerText(timerSeekBar.progress)
    }
}

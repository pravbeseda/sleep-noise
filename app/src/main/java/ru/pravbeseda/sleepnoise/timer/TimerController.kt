package ru.pravbeseda.sleepnoise.timer

import android.os.CountDownTimer

class TimerController(private val onTick: (String) -> Unit, private val onTime: () -> Unit) {

    private var timer: CountDownTimer? = null

    fun startTimer(durationInMinutes: Int) {
        val durationInMillis = (durationInMinutes * 60 * 1000).toLong()
        timer = object : CountDownTimer(durationInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                onTick(SleepTimer.formatRemaining(millisUntilFinished))
            }

            override fun onFinish() {
                onTime()
            }
        }.start()
    }

    fun stopTimer() {
        timer?.cancel()
    }
}

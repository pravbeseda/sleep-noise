package ru.pravbeseda.sleepnoise.timer

import android.os.CountDownTimer

class TimerController(private val onTick: (String) -> Unit, private val onFinish: () -> Unit) {

    private var timer: CountDownTimer? = null

    fun startTimer(durationInMinutes: Int) {
        val durationInMillis = (durationInMinutes * 60 * 1000).toLong()
        timer = object : CountDownTimer(durationInMillis, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                val hours = millisUntilFinished / 1000 / 60 / 60
                val minutes = (millisUntilFinished / 1000 / 60) % 60
                val seconds = (millisUntilFinished / 1000) % 60

                val formattedTime = if (hours > 0) {
                    String.format("%02d:%02d:%02d", hours, minutes, seconds)
                } else {
                    String.format("%02d:%02d", minutes, seconds)
                }

                onTick(formattedTime)
            }

            override fun onFinish() {
                onFinish()
            }
        }.start()
    }

    fun stopTimer() {
        timer?.cancel()
    }
}

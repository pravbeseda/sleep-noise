package ru.pravbeseda.sleepnoise.timer

import android.content.Context
import android.content.SharedPreferences

class TimerPreferences(context: Context) {
    companion object {
        private const val PREFS_NAME = "timer_prefs"
        private const val TIMER_VALUE_KEY = "timer_value"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveTimerValue(minutes: Int) {
        prefs.edit().putInt(TIMER_VALUE_KEY, minutes).apply()
    }

    fun getTimerValue(): Int {
        return prefs.getInt(TIMER_VALUE_KEY, 0)
    }
}
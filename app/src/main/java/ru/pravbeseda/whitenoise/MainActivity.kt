package ru.pravbeseda.whitenoise

import android.content.SharedPreferences
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.widget.PopupMenu
import ru.pravbeseda.whitenoise.media.BrownNoiseGenerator
import ru.pravbeseda.whitenoise.media.WhiteNoiseGenerator

const val APP_PREFS = "AppPreferences"
const val WHITE_NOISE_VOLUME = "whiteNoiseVolume"
const val BROWN_NOISE_VOLUME = "brownNoiseVolume"
const val CURRENT_THEME = "selectedTheme"

class MainActivity : AppCompatActivity() {
    private val whiteNoiseGenerator = WhiteNoiseGenerator()
    private val brownNoiseGenerator = BrownNoiseGenerator()
    private var isPlaying = false
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = getSharedPreferences(APP_PREFS, MODE_PRIVATE)
        applyTheme(preferences.getString(CURRENT_THEME, "system") ?: "system")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val playPauseButton: Button = findViewById(R.id.playPauseButton)
        val whiteNoiseVolume: SeekBar = findViewById(R.id.whiteNoiseVolume)
        val brownNoiseVolume: SeekBar = findViewById(R.id.brownNoiseVolume)

        val whiteVolume = preferences.getFloat(WHITE_NOISE_VOLUME, 0.5f)
        val brownVolume = preferences.getFloat(BROWN_NOISE_VOLUME, 0.5f)

        whiteNoiseVolume.progress = (whiteVolume * 100).toInt()
        brownNoiseVolume.progress = (brownVolume * 100).toInt()
        whiteNoiseGenerator.setVolume(whiteVolume)
        brownNoiseGenerator.setVolume(brownVolume)

        playPauseButton.setOnClickListener {
            if (isPlaying) {
                stopNoise()
                playPauseButton.text = "Play Noise"
            } else {
                startNoise()
                playPauseButton.text = "Stop Noise"
            }
            isPlaying = !isPlaying
        }

        whiteNoiseVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val volume = progress / 100f
                whiteNoiseGenerator.setVolume(volume)
                saveVolume(WHITE_NOISE_VOLUME, volume)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        brownNoiseVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val volume = progress / 100f
                brownNoiseGenerator.setVolume(volume)
                saveVolume(BROWN_NOISE_VOLUME, volume)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_theme, menu)
        updateThemeIcon(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.theme_button -> {
                showThemePopup(findViewById(R.id.theme_button))
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun showThemePopup(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_theme_popup, popup.menu)

        val currentTheme = preferences.getString(CURRENT_THEME, "system") ?: "system"
        when (currentTheme) {
            "system" -> popup.menu.findItem(R.id.theme_system).isChecked = true
            "light" -> popup.menu.findItem(R.id.theme_light).isChecked = true
            "dark" -> popup.menu.findItem(R.id.theme_dark).isChecked = true
        }

        popup.setOnMenuItemClickListener { menuItem ->
            for (i in 0 until popup.menu.size()) {
                popup.menu.getItem(i).isChecked = false
            }
            menuItem.isChecked = true

            when (menuItem.itemId) {
                R.id.theme_system -> {
                    setThemePreference("system")
                }
                R.id.theme_light -> {
                    setThemePreference("light")
                }
                R.id.theme_dark -> {
                    setThemePreference("dark")
                }
            }
            true
        }

        popup.show()
    }

    private fun setThemePreference(theme: String) {
        preferences.edit().putString(CURRENT_THEME, theme).apply()
        recreate()
    }

    private fun saveVolume(key: String, volume: Float) {
        preferences.edit().putFloat(key, volume).apply()
    }

    private fun startNoise() {
        whiteNoiseGenerator.startNoise()
        brownNoiseGenerator.startNoise()
    }

    private fun stopNoise() {
        whiteNoiseGenerator.stopNoise()
        brownNoiseGenerator.stopNoise()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopNoise()
    }

    private fun applyTheme(theme: String) {
        when (theme) {
            "system" -> setTheme(R.style.Theme_WhiteNoise_System)
            "light" -> setTheme(R.style.Theme_WhiteNoise_Light)
            "dark" -> setTheme(R.style.Theme_WhiteNoise_Dark)
        }
    }

    private fun updateThemeIcon(menu: Menu?) {
        val currentTheme = preferences.getString(CURRENT_THEME, "system") ?: "system"
        val themeItem = menu?.findItem(R.id.theme_button)

        when (currentTheme) {
            "system" -> themeItem?.setIcon(R.drawable.ic_theme_system)
            "light" -> themeItem?.setIcon(R.drawable.ic_theme_light)
            "dark" -> themeItem?.setIcon(R.drawable.ic_theme_dark)
        }
    }
}


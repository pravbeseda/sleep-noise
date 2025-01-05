package ru.pravbeseda.sleepnoise

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import ru.pravbeseda.sleepnoise.media.BrownNoiseGenerator
import ru.pravbeseda.sleepnoise.media.WhiteNoiseGenerator
import ru.pravbeseda.sleepnoise.models.Language
import ru.pravbeseda.sleepnoise.adapters.LanguagesArrayAdapter
import ru.pravbeseda.sleepnoise.timer.TimerController
import ru.pravbeseda.sleepnoise.timer.TimerView
import java.util.Locale

const val APP_PREFS = "AppPreferences"
const val WHITE_NOISE_VOLUME = "whiteNoiseVolume"
const val BROWN_NOISE_VOLUME = "brownNoiseVolume"
const val CURRENT_THEME = "selectedTheme"
const val CURRENT_LANGUAGE = "selectedLanguage"

class MainActivity : AppCompatActivity() {
    private val whiteNoiseGenerator = WhiteNoiseGenerator()
    private val brownNoiseGenerator = BrownNoiseGenerator()
    private lateinit var playButton: Button
    private lateinit var timerView: TimerView
    private lateinit var timerController: TimerController
    private var isPlaying = false
    private lateinit var preferences: SharedPreferences
    private lateinit var whiteNoiseLabel: TextView
    private lateinit var brownNoiseLabel: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = getSharedPreferences(APP_PREFS, MODE_PRIVATE)
        val currentTheme = preferences.getString(CURRENT_THEME, "dark") ?: "dark"
        applyTheme(currentTheme)
        applyLanguage(preferences.getString(CURRENT_LANGUAGE, "en") ?: "en")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        supportActionBar?.title = getString(R.string.app_name)

        val versionTextView: TextView = findViewById(R.id.version_text)
        val versionName = BuildConfig.VERSION_NAME
        val versionString = getString(R.string.version, versionName)
        versionTextView.text = versionString

        playButton = findViewById(R.id.playButton)

        timerView = findViewById(R.id.timerView)

        timerController = TimerController(
            onTick = { time -> timerView.showCountdown(time) },
            onTime = { stopPlayback() }
        )


        val whiteNoiseVolume: SeekBar = findViewById(R.id.whiteNoiseVolume)
        val brownNoiseVolume: SeekBar = findViewById(R.id.brownNoiseVolume)
        whiteNoiseLabel = findViewById(R.id.whiteNoiseLabel)
        brownNoiseLabel = findViewById(R.id.brownNoiseLabel)

        val whiteVolume = preferences.getFloat(WHITE_NOISE_VOLUME, 0.0f)
        val brownVolume = preferences.getFloat(BROWN_NOISE_VOLUME, 0.5f)

        whiteNoiseVolume.progress = (whiteVolume * 100).toInt()
        brownNoiseVolume.progress = (brownVolume * 100).toInt()
        setWhiteNoiseVolume(whiteVolume)
        setBrownNoiseVolume(brownVolume)

        playButton.setOnClickListener {
            if (isPlaying) {
                stopPlayback()
            } else {
                startPlayback()
            }
        }

        whiteNoiseVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val volume = progress / 100f
                setWhiteNoiseVolume(volume)
                saveVolume(WHITE_NOISE_VOLUME, volume)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        brownNoiseVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val volume = progress / 100f
                setBrownNoiseVolume(volume)
                saveVolume(BROWN_NOISE_VOLUME, volume)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        updateThemeIcon(menu)
        try {
            // hack to show icons in popup menu
            if (menu is MenuBuilder) {
                menu.setOptionalIconsVisible(true)
            }
        } catch (e: ClassCastException) {
            e.printStackTrace()
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.theme_button -> {
                showThemePopup(findViewById(R.id.theme_button))
                true
            }
            R.id.language_button -> {
                languageSelection()
                true
            }
            R.id.mail -> {
                mailToMe()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun startPlayback() {
        isPlaying = true
        playButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_pause, 0, 0)
        timerView.setPlayingState(true)

        val timerValue = timerView.getTimerValueInMinutes()
        if (timerValue > 0) {
            timerController.startTimer(timerValue)
        }

        startNoise()
    }

    private fun stopPlayback() {
        isPlaying = false
        playButton.setCompoundDrawablesWithIntrinsicBounds(0, R.drawable.ic_play, 0, 0)
        timerController.stopTimer()
        timerView.setPlayingState(false)

        stopNoise()
    }

    private fun showThemePopup(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_theme_popup, popup.menu)

        val currentTheme = preferences.getString(CURRENT_THEME, "dark") ?: "dark"
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
            "system" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
                setTheme(R.style.Theme_SleepNoise_System)
            }
            "light" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
                setTheme(R.style.Theme_SleepNoise_Light)
            }
            "dark" -> {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
                setTheme(R.style.Theme_SleepNoise_Dark)
            }
        }
    }

    private fun updateThemeIcon(menu: Menu?) {
        val currentTheme = preferences.getString(CURRENT_THEME, "dark") ?: "dark"
        val themeItem = menu?.findItem(R.id.theme_button)

        when (currentTheme) {
            "system" -> themeItem?.setIcon(R.drawable.ic_theme_system)
            "light" -> themeItem?.setIcon(R.drawable.ic_theme_light)
            "dark" -> themeItem?.setIcon(R.drawable.ic_theme_dark)
        }
    }

    private fun setWhiteNoiseVolume(volume: Float) {
        whiteNoiseGenerator.setVolume(volume)
        whiteNoiseLabel.text = getString(R.string.white_noise_volume, (volume * 100).toInt())
    }

    private fun setBrownNoiseVolume(volume: Float) {
        brownNoiseGenerator.setVolume(volume)
        brownNoiseLabel.text = getString(R.string.brown_noise_volume, (volume * 100).toInt())
    }

    private fun languageSelection() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.select_language)
        val languages = arrayOf(
            Language("en", R.drawable.flag_united_kingdom, R.string.english),
            Language("ru", R.drawable.flag_russia, R.string.russian, "Russian"),
            Language("", R.drawable.flag_united_nations, R.string.another_language),
        )
        var selected = languages.indexOfFirst { it.code == getString(R.string.lang) }
        val listAdapter = LanguagesArrayAdapter(this, languages)
        builder.setSingleChoiceItems(listAdapter, selected) { _: DialogInterface, i: Int ->
            selected = i
        }
        builder.setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
            if (languages[selected].code != "") {
                setLanguage(languages[selected].code)
                recreate()
            } else {
                showNewLanguageMessage()
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.create().show()
    }

    fun setLanguage(language: String?) {
        val lang = if (!language.isNullOrBlank()) {
            val ed = preferences.edit()
            ed.putString(CURRENT_LANGUAGE, language)
            ed.apply()
            language
        } else {
            val savedLang = preferences.getString(CURRENT_LANGUAGE, null)
            if (!savedLang.isNullOrBlank()) {
                savedLang
            } else {
                getString(R.string.lang)
            }
        }
        applyLanguage(lang)
    }

    private fun applyLanguage(languageCode: String) {
        val locale = Locale(languageCode)
        Locale.setDefault(locale)
        val config = Configuration()
        config.setLocale(locale)
        resources.updateConfiguration(config, resources.displayMetrics)
    }

    private fun showNewLanguageMessage() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.title_language_need)
        builder.setMessage(R.string.text_language_need)
        builder.setPositiveButton(R.string.mail) { _, _ ->
            mailToMe()
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    private fun mailToMe() {
        val intent = Intent(Intent.ACTION_SEND)
        intent.type = "plain/text"
        intent.putExtra(Intent.EXTRA_EMAIL, arrayOf("kalugaman@gmail.com"))
        intent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.app_name))
        intent.putExtra(Intent.EXTRA_TEXT, getDebugInfo())
        startActivity(Intent.createChooser(intent, getString(R.string.mail_choose)))
    }

    private fun getDebugInfo(): String {
        var appVersion = ""
        try {
            val pInfo = this.packageManager.getPackageInfo(packageName, 0)
            appVersion = pInfo.versionName.toString()
        } catch (e: PackageManager.NameNotFoundException) {
            e.printStackTrace()
        }
        var res = "\ndevice: " + Build.DEVICE
        res += "\nmodel: " + Build.MODEL
        res += "\nSDK: " + Build.VERSION.SDK_INT
        res += "\nOSVer: " + Build.VERSION.RELEASE
        if (appVersion != "") res += "\nAppVer: $appVersion"
        res += "\n\n"
        return res
    }
}


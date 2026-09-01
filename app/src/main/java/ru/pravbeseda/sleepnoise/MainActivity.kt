package ru.pravbeseda.sleepnoise

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.BidiFormatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.core.os.LocaleListCompat
import androidx.core.view.WindowCompat
import com.google.firebase.crashlytics.FirebaseCrashlytics
import ru.pravbeseda.sleepnoise.adapters.LanguagesArrayAdapter
import ru.pravbeseda.sleepnoise.models.Language
import ru.pravbeseda.sleepnoise.playback.PlaybackService
import ru.pravbeseda.sleepnoise.timer.TimerController
import ru.pravbeseda.sleepnoise.timer.TimerView

const val APP_PREFS = "AppPreferences"
const val WHITE_NOISE_VOLUME = "whiteNoiseVolume"
const val BROWN_NOISE_VOLUME = "brownNoiseVolume"
const val CURRENT_THEME = "selectedTheme"
const val CURRENT_LANGUAGE = "selectedLanguage"
const val DEFAULT_WHITE_NOISE_VOLUME = 0.0f
const val DEFAULT_BROWN_NOISE_VOLUME = 0.5f

class MainActivity : AppCompatActivity() {
    private lateinit var playButton: Button
    private lateinit var timerView: TimerView
    private lateinit var timerController: TimerController
    private var isPlaying = false
    private lateinit var preferences: SharedPreferences
    private lateinit var whiteNoiseLabel: TextView
    private lateinit var brownNoiseLabel: TextView
    private var whiteVolume = DEFAULT_WHITE_NOISE_VOLUME
    private var brownVolume = DEFAULT_BROWN_NOISE_VOLUME
    private var playbackBinder: PlaybackService.LocalBinder? = null

    private val playbackConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? PlaybackService.LocalBinder ?: return
            playbackBinder = binder
            binder.listener = PlaybackService.Listener { onPlaybackStoppedByService() }
            binder.setWhiteVolume(whiteVolume)
            binder.setBrownVolume(brownVolume)
            showPlayingState(binder.isPlaying)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = getSharedPreferences(APP_PREFS, MODE_PRIVATE)
        val currentTheme = preferences.getString(CURRENT_THEME, "dark") ?: "dark"
        applyTheme(currentTheme)
        applyLanguage(preferences.getString(CURRENT_LANGUAGE, "en") ?: "en")

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WindowCompat.enableEdgeToEdge(window)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = currentTheme != "dark"

        supportActionBar?.title = getString(R.string.app_name)

        val versionTextView: TextView = findViewById(R.id.version_text)
        val versionName = BuildConfig.VERSION_NAME
        val versionString = getString(R.string.version, versionName)
        versionTextView.text = versionString

        playButton = findViewById(R.id.playButton)

        timerView = findViewById(R.id.timerView)

        timerController = TimerController(
            onTick = { time -> timerView.showCountdown(time) },
            onTime = { stopPlayback() },
        )

        val whiteNoiseVolume: SeekBar = findViewById(R.id.whiteNoiseVolume)
        val brownNoiseVolume: SeekBar = findViewById(R.id.brownNoiseVolume)
        whiteNoiseLabel = findViewById(R.id.whiteNoiseLabel)
        brownNoiseLabel = findViewById(R.id.brownNoiseLabel)

        val savedWhiteVolume = preferences.getFloat(WHITE_NOISE_VOLUME, DEFAULT_WHITE_NOISE_VOLUME)
        val savedBrownVolume = preferences.getFloat(BROWN_NOISE_VOLUME, DEFAULT_BROWN_NOISE_VOLUME)

        whiteNoiseVolume.progress = (savedWhiteVolume * 100).toInt()
        brownNoiseVolume.progress = (savedBrownVolume * 100).toInt()
        setWhiteNoiseVolume(savedWhiteVolume)
        setBrownNoiseVolume(savedBrownVolume)

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
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        updateThemeIcon(menu)
        // hack to show icons in popup menu
        if (menu is MenuBuilder) {
            menu.setOptionalIconsVisible(true)
        }

        // Add (Language) for non-English languages
        val languageItem = menu.findItem(R.id.language_button)
        val currentLangCode = getString(R.string.lang)
        val baseTitle = getString(R.string.language)
        if (currentLangCode != "en") {
            val bidi = BidiFormatter.getInstance()
            val langSuffix = bidi.unicodeWrap("(Language)")
            languageItem.title = "$baseTitle $langSuffix"
        } else {
            languageItem.title = baseTitle
        }

        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean = when (item.itemId) {
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

        R.id.credits -> {
            showCreditsDialog()
            true
        }

        else -> super.onOptionsItemSelected(item)
    }

    override fun onStart() {
        super.onStart()
        bindService(Intent(this, PlaybackService::class.java), playbackConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        playbackBinder?.listener = null
        playbackBinder = null
        unbindService(playbackConnection)
    }

    private fun startPlayback() {
        showPlayingState(true)

        val timerValue = timerView.getTimerValueInMinutes()
        if (timerValue > 0) {
            timerController.startTimer(timerValue)
        }

        ContextCompat.startForegroundService(this, playbackIntent(PlaybackService.ACTION_START))
    }

    private fun stopPlayback() {
        timerController.stopTimer()
        showPlayingState(false)

        startService(playbackIntent(PlaybackService.ACTION_STOP))
    }

    /** The service stopped itself — the notification's Stop action — so only the UI is left to catch up. */
    private fun onPlaybackStoppedByService() {
        timerController.stopTimer()
        showPlayingState(false)
    }

    private fun showPlayingState(playing: Boolean) {
        isPlaying = playing
        val icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        playButton.setCompoundDrawablesWithIntrinsicBounds(0, icon, 0, 0)
        timerView.setPlayingState(playing)
    }

    private fun playbackIntent(action: String): Intent = Intent(this, PlaybackService::class.java).setAction(action)

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
        whiteVolume = volume
        playbackBinder?.setWhiteVolume(volume)
        whiteNoiseLabel.text = getString(R.string.white_noise_volume, (volume * 100).toInt())
    }

    private fun setBrownNoiseVolume(volume: Float) {
        brownVolume = volume
        playbackBinder?.setBrownVolume(volume)
        brownNoiseLabel.text = getString(R.string.brown_noise_volume, (volume * 100).toInt())
    }

    private fun languageSelection() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.select_language)
        val languages = arrayOf(
            Language("ar", R.drawable.ic_arabic, R.string.arabic, "Arabic"),
            Language("en", R.drawable.flag_united_kingdom, R.string.english),
            Language("de", R.drawable.flag_germany, R.string.german, "German"),
            Language("ru", R.drawable.flag_russia, R.string.russian, "Russian"),
            Language("es", R.drawable.flag_spain, R.string.spanish, "Spanish"),
            Language("uk", R.drawable.flag_ukraine, R.string.ukrainian, "Ukrainian"),
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

    private fun setLanguage(language: String?) {
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
        val locales = LocaleListCompat.forLanguageTags(languageCode)
        AppCompatDelegate.setApplicationLocales(locales)
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
        val email = "kalugaman@gmail.com"
        val subject = getString(R.string.app_name)
        val body = getDebugInfo()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$email")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            putExtra(Intent.EXTRA_TEXT, body)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.mail_choose)))
    }

    private fun getDebugInfo(): String {
        var appVersion = ""
        try {
            val pInfo = this.packageManager.getPackageInfo(packageName, 0)
            appVersion = pInfo.versionName.toString()
        } catch (e: PackageManager.NameNotFoundException) {
            // The app asking for its own package and not finding it should be impossible.
            FirebaseCrashlytics.getInstance().recordException(e)
        }
        var res = "\ndevice: " + Build.DEVICE
        res += "\nmodel: " + Build.MODEL
        res += "\nSDK: " + Build.VERSION.SDK_INT
        res += "\nOSVer: " + Build.VERSION.RELEASE
        if (appVersion != "") res += "\nAppVer: $appVersion"
        res += "\n\n"
        return res
    }

    private fun showCreditsDialog() {
        val dialog = CreditsDialogFragment.newInstance()
        dialog.show(supportFragmentManager, "credits")
    }
}

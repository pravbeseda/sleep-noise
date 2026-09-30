package ru.pravbeseda.sleepnoise

import android.Manifest
import android.annotation.SuppressLint
import android.content.DialogInterface
import android.os.Build
import android.os.Bundle
import android.text.BidiFormatter
import android.view.Menu
import android.view.MenuItem
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.pravbeseda.sleepnoise.adapters.LanguagesArrayAdapter
import ru.pravbeseda.sleepnoise.playback.PlaybackState
import ru.pravbeseda.sleepnoise.playback.PlaybackViewModel
import ru.pravbeseda.sleepnoise.settings.LocaleController
import ru.pravbeseda.sleepnoise.settings.ThemeController
import ru.pravbeseda.sleepnoise.support.FeedbackMail
import ru.pravbeseda.sleepnoise.timer.TimerView
import ru.pravbeseda.sleepnoise.ui.NoiseControlView
import ru.pravbeseda.sleepnoise.ui.NoiseRows

class MainActivity : AppCompatActivity() {
    private lateinit var playButton: ImageButton
    private lateinit var timerView: TimerView
    private lateinit var themeController: ThemeController
    private lateinit var localeController: LocaleController
    private val playback: PlaybackViewModel by viewModels()

    /**
     * Every noise's row, by the key that noise stores its level under. The rows are built from the
     * registries instead of being declared in the layout, so this is what a test reaches for when it wants
     * the row belonging to one particular noise.
     */
    lateinit var noiseRows: Map<String, NoiseControlView>
        private set

    // The answer is not read: the foreground service plays either way, a denial only costs the
    // user the ongoing notification and its Stop action.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        themeController = ThemeController(this)
        localeController = LocaleController(this)
        setTheme(themeController.style)
        localeController.applyStored()

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        WindowCompat.enableEdgeToEdge(window)
        // Both themes are dark ones, so the status bar always wants light icons on top of them.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false

        supportActionBar?.title = getString(R.string.app_name)

        val versionTextView: TextView = findViewById(R.id.version_text)
        versionTextView.text = getString(R.string.version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        playButton = findViewById(R.id.playButton)

        timerView = findViewById(R.id.timerView)
        timerView.onMinutesChanged = playback::setTimerMinutes

        noiseRows = NoiseRows.build(findViewById(R.id.noiseContainer), findViewById(R.id.noiseLabContainer), playback::setVolume)

        playButton.setOnClickListener {
            if (playback.state.value.audible) {
                playback.stop()
            } else {
                askForNotificationPermission()
                playback.start()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                playback.state.collect(::render)
            }
        }
    }

    @SuppressLint("RestrictedApi")
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu, menu)
        menu.findItem(R.id.theme_button)?.setIcon(themeController.icon)
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
            themeController.switchToNext()
            recreate()
            true
        }

        R.id.language_button -> {
            languageSelection()
            true
        }

        R.id.mail -> {
            startActivity(FeedbackMail.chooser(this))
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
        playback.connect()
    }

    override fun onStop() {
        super.onStop()
        // A recreate() keeps the binding, and with it the state the next instance shows from its first frame.
        if (!isChangingConfigurations) playback.disconnect()
    }

    /**
     * A paused session is still a session: the countdown stays on screen and the seekbar stays hidden,
     * and only the button changes, so that pressing it asks the service to resume rather than to stop.
     */
    private fun render(state: PlaybackState) {
        playButton.setImageResource(if (state.audible) R.drawable.ic_pause else R.drawable.ic_play)
        timerView.minutes = state.timerMinutes
        timerView.setPlayingState(state.playing)
        if (state.remainingMillis > 0) timerView.showCountdown(state.remainingMillis)
    }

    // The contract itself short-circuits when the permission is already held, so there is nothing to check first.
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun languageSelection() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.select_language)
        val languages = localeController.languages
        var selected = languages.indexOfFirst { it.code == getString(R.string.lang) }
        val listAdapter = LanguagesArrayAdapter(this, languages.toTypedArray())
        builder.setSingleChoiceItems(listAdapter, selected) { _: DialogInterface, i: Int ->
            selected = i
        }
        builder.setPositiveButton(R.string.ok) { _: DialogInterface, _: Int ->
            if (languages[selected].code != "") {
                localeController.select(languages[selected].code)
            } else {
                showNewLanguageMessage()
            }
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.create().show()
    }

    private fun showNewLanguageMessage() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle(R.string.title_language_need)
        builder.setMessage(R.string.text_language_need)
        builder.setPositiveButton(R.string.mail) { _, _ ->
            startActivity(FeedbackMail.chooser(this))
        }
        builder.setNegativeButton(R.string.cancel, null)
        builder.show()
    }

    private fun showCreditsDialog() {
        val dialog = CreditsDialogFragment.newInstance()
        dialog.show(supportFragmentManager, "credits")
    }
}

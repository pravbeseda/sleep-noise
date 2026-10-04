package ru.pravbeseda.sleepnoise

import android.Manifest
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.text.BidiFormatter
import android.view.Menu
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import ru.pravbeseda.sleepnoise.playback.PlaybackState
import ru.pravbeseda.sleepnoise.playback.PlaybackViewModel
import ru.pravbeseda.sleepnoise.review.ReviewPrompt
import ru.pravbeseda.sleepnoise.settings.DEFAULT_LANGUAGE
import ru.pravbeseda.sleepnoise.settings.LocaleController
import ru.pravbeseda.sleepnoise.settings.ThemeController
import ru.pravbeseda.sleepnoise.settings.settingsRepository
import ru.pravbeseda.sleepnoise.support.FeedbackMail
import ru.pravbeseda.sleepnoise.support.PlayStorePage
import ru.pravbeseda.sleepnoise.timer.TimerView
import ru.pravbeseda.sleepnoise.ui.CreditsDialogFragment
import ru.pravbeseda.sleepnoise.ui.LanguageDialog
import ru.pravbeseda.sleepnoise.ui.NoiseControlView
import ru.pravbeseda.sleepnoise.ui.NoiseRows
import ru.pravbeseda.sleepnoise.update.UpdatePrompt

class MainActivity : AppCompatActivity() {
    private lateinit var playButton: ImageButton
    private lateinit var timerView: TimerView
    private lateinit var themeController: ThemeController
    private lateinit var localeController: LocaleController
    private lateinit var reviewPrompt: ReviewPrompt
    private lateinit var updatePrompt: UpdatePrompt
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
        val settings = settingsRepository(this)
        themeController = ThemeController(settings)
        localeController = LocaleController(settings)
        setTheme(themeController.style)

        super.onCreate(savedInstanceState)
        localeController.adoptLegacyLanguage()
        reviewPrompt = ReviewPrompt(this, settings)
        updatePrompt = UpdatePrompt(this, settings)
        setContentView(R.layout.activity_main)

        WindowCompat.enableEdgeToEdge(window)
        // Both themes are dark ones, so the status bar always wants light icons on top of them.
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        keepContentClearOfTheBars()

        supportActionBar?.title = getString(R.string.app_name)

        val versionTextView: TextView = findViewById(R.id.version_text)
        versionTextView.text = getString(R.string.version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)

        playButton = findViewById(R.id.playButton)

        timerView = findViewById(R.id.timerView)
        timerView.onMinutesChanged = playback::setTimerMinutes

        noiseRows = NoiseRows.build(settings, findViewById(R.id.noiseContainer), findViewById(R.id.noiseLabContainer), playback::setVolume)

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
                playback.state.collect { state ->
                    render(state)
                    // Only once the service has answered: a cold start reads as stopped while the noise may be playing.
                    // An update offer goes first, and the rating prompt waits for an open with nothing to offer.
                    updatePrompt.onPlaybackChanged(nothingPlays = state.confirmed && !state.playing, otherwise = reviewPrompt::askIfDue)
                }
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
        if (currentLangCode != DEFAULT_LANGUAGE) {
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
            LanguageDialog.show(this, localeController)
            true
        }

        R.id.rate -> {
            PlayStorePage.open(this)
            true
        }

        R.id.mail -> {
            startActivity(FeedbackMail.chooser(this))
            true
        }

        R.id.credits -> {
            CreditsDialogFragment().show(supportFragmentManager, "credits")
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

    /**
     * Holds the screen's scroll off the system bars and the display cutout with margins, not padding:
     * ScrollView brings a child into view against its own height and ignores its padding, so insets held
     * as padding left a focused control partly under the navigation bar (issue #55).
     */
    private fun keepContentClearOfTheBars() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.contentScroll)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> { setMargins(bars.left, bars.top, bars.right, bars.bottom) }
            WindowInsetsCompat.CONSUMED
        }
    }

    // The contract itself short-circuits when the permission is already held, so there is nothing to check first.
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

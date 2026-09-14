package ru.pravbeseda.sleepnoise

import android.Manifest
import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.DialogInterface
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.BidiFormatter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.view.menu.MenuBuilder
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import ru.pravbeseda.sleepnoise.adapters.LanguagesArrayAdapter
import ru.pravbeseda.sleepnoise.catalog.DEFAULT_LAB_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.catalog.NOISE_LAB_CANDIDATES
import ru.pravbeseda.sleepnoise.catalog.NOISE_LAB_ENABLED
import ru.pravbeseda.sleepnoise.catalog.NoiseLabCandidate
import ru.pravbeseda.sleepnoise.catalog.SHIPPING_NOISES
import ru.pravbeseda.sleepnoise.catalog.ShippingNoise
import ru.pravbeseda.sleepnoise.playback.PlaybackService
import ru.pravbeseda.sleepnoise.settings.APP_PREFS
import ru.pravbeseda.sleepnoise.settings.LocaleController
import ru.pravbeseda.sleepnoise.settings.ThemeController
import ru.pravbeseda.sleepnoise.support.FeedbackMail
import ru.pravbeseda.sleepnoise.timer.TimerView
import ru.pravbeseda.sleepnoise.ui.NoiseControl
import ru.pravbeseda.sleepnoise.ui.NoiseControlView
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var playButton: ImageButton
    private lateinit var timerView: TimerView
    private var isPlaying = false
    private lateinit var preferences: SharedPreferences
    private lateinit var themeController: ThemeController
    private lateinit var localeController: LocaleController
    private var playbackBinder: PlaybackService.LocalBinder? = null
    private val rowsByVolumeKey = LinkedHashMap<String, NoiseControlView>()

    /**
     * Every noise's row, by the key that noise stores its level under. The rows are built from the
     * registries instead of being declared in the layout, so this is what a test reaches for when it wants
     * the row belonging to one particular noise.
     */
    val noiseRows: Map<String, NoiseControlView> get() = rowsByVolumeKey

    // The answer is not read: the foreground service plays either way, a denial only costs the
    // user the ongoing notification and its Stop action.
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val playbackListener = object : PlaybackService.Listener {
        override fun onTick(remainingMillis: Long) {
            timerView.showCountdown(remainingMillis)
        }

        /** Silent while another app holds the output: the button offers to start it again. */
        override fun onPaused(paused: Boolean) {
            showPausedState(paused)
        }

        /** Every stop: the notification's Stop action, the sleep timer expiring, or the ACTION_STOP sent here. */
        override fun onPlaybackStopped() {
            showPlayingState(false)
        }
    }

    private val playbackConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? PlaybackService.LocalBinder ?: return
            playbackBinder = binder
            binder.listener = playbackListener
            showPlayingState(binder.isPlaying)
            // Only when true: showPausedState(false) means "playing again", which a stopped service is not.
            if (binder.isPaused) showPausedState(true)
            // Non-zero only while the service is playing with a timer, so it needs no further guard.
            if (binder.remainingMillis > 0) {
                timerView.showCountdown(binder.remainingMillis)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playbackBinder = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = getSharedPreferences(APP_PREFS, MODE_PRIVATE)
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

        addNoiseControls()

        playButton.setOnClickListener {
            if (isPlaying) {
                stopPlayback()
            } else {
                startPlayback()
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
        bindService(Intent(this, PlaybackService::class.java), playbackConnection, BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        // The service clears the listener in onUnbind, which is the only way out of a binding.
        playbackBinder = null
        unbindService(playbackConnection)
    }

    private fun startPlayback() {
        askForNotificationPermission()
        showPlayingState(true)

        val startIntent = playbackIntent(PlaybackService.ACTION_START)
            .putExtra(PlaybackService.EXTRA_TIMER_MINUTES, timerView.getTimerValueInMinutes())
        ContextCompat.startForegroundService(this, startIntent)
    }

    private fun stopPlayback() {
        showPlayingState(false)

        startService(playbackIntent(PlaybackService.ACTION_STOP))
    }

    private fun showPlayingState(playing: Boolean) {
        isPlaying = playing
        showPlayButtonIcon(playing)
        timerView.setPlayingState(playing)
    }

    /**
     * A paused session is still a session: the countdown stays on screen and the seekbar stays
     * hidden, and only the button changes, so that pressing it asks the service to resume rather
     * than to stop.
     */
    private fun showPausedState(paused: Boolean) {
        isPlaying = !paused
        showPlayButtonIcon(!paused)
    }

    private fun showPlayButtonIcon(playing: Boolean) {
        val icon = if (playing) R.drawable.ic_pause else R.drawable.ic_play
        playButton.setImageResource(icon)
    }

    // The contract itself short-circuits when the permission is already held, so there is nothing to check first.
    private fun askForNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun playbackIntent(action: String): Intent = Intent(this, PlaybackService::class.java).setAction(action)

    /**
     * One [NoiseControlView] per noise, built from the two registries rather than declared in the layout:
     * the shipping ones from [SHIPPING_NOISES], then the lab's candidates when it is switched on. A new
     * noise is an entry in a registry and nothing else — a row wired up by hand here is the mistake the
     * component replaced.
     *
     * Both registries are read straight out of `catalog/` rather than through the service binder: this runs
     * in onCreate and the binder does not arrive until after onStart, so a registry behind it would draw
     * nothing.
     */
    private fun addNoiseControls() {
        SHIPPING_NOISES.forEach { noise -> addNoiseControl(R.id.noiseContainer, shippingNoiseControl(noise)) }
        if (!NOISE_LAB_ENABLED) return
        findViewById<LinearLayout>(R.id.noiseLabContainer).visibility = View.VISIBLE
        NOISE_LAB_CANDIDATES.forEach { candidate -> addNoiseControl(R.id.noiseLabContainer, labNoiseControl(candidate)) }
    }

    /**
     * The engine hears the level only while the noise is switched on; the component decides which it is.
     * The row is kept under the noise's own key, which is how anything outside this method finds it again.
     */
    private fun addNoiseControl(containerId: Int, noise: NoiseControl) {
        // A vertical LinearLayout already gives a child MATCH_PARENT x WRAP_CONTENT, which is what a row wants.
        val control = NoiseControlView(this)
        findViewById<LinearLayout>(containerId).addView(control)
        rowsByVolumeKey[noise.volumeKey] = control
        control.bind(noise, preferences) { volume -> playbackBinder?.setVolume(noise.volumeKey, volume) }
    }

    private fun shippingNoiseControl(noise: ShippingNoise) = NoiseControl(
        noise.volumeKey,
        noise.enabledKey,
        noise.defaultVolume,
        getString(noise.nameRes),
    ) { percent -> getString(noise.volumeLabelRes, percent) }

    /** The name is developer-facing debug copy on the descriptor, so it is a literal rather than a string resource. */
    private fun labNoiseControl(candidate: NoiseLabCandidate) = NoiseControl(
        candidate.preferenceKey,
        candidate.enabledPreferenceKey,
        DEFAULT_LAB_NOISE_VOLUME,
        candidate.label,
    ) { percent -> String.format(Locale.getDefault(), "%s: %d%%", candidate.label, percent) }

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
                recreate()
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

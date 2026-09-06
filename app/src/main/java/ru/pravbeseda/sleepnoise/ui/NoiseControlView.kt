package ru.pravbeseda.sleepnoise.ui

import android.content.Context
import android.content.SharedPreferences
import android.os.Parcelable
import android.util.AttributeSet
import android.util.SparseArray
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.AppCompatCheckBox
import androidx.core.content.edit
import ru.pravbeseda.sleepnoise.DEFAULT_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.R

/** A seekbar's range as a volume. */
private const val PERCENT_SCALE = 100f

/** What a switched-off noise's label and slider fade to: plainly off, still readable, still movable. */
private const val DISABLED_CONTROLS_ALPHA = 0.5f

/** The level a noise switched on from silence lands at: the quietest one that is not silence. */
private const val MIN_AUDIBLE_PROGRESS = 1

/**
 * One noise as its controls need to know it: where its level and its on/off state are stored, what
 * the toggle announces, and how the level reads above the slider.
 */
class NoiseControl(
    val volumeKey: String,
    val enabledKey: String,
    val defaultVolume: Float,
    /** The noise's name, which is what the toggle announces to a screen reader. */
    val name: CharSequence,
    /** The text over the slider, given the level as a percentage. */
    val label: (Int) -> CharSequence,
)

/**
 * One noise's settings: a speaker that silences the noise without touching its level, and a slider
 * that sets that level, with the level itself read out above it.
 *
 * Every noise on the screen is one of these — the two the app ships with and each experiment of the
 * noise lab alike — so a new noise gets its toggle, its spacing and its persistence by existing
 * rather than by being wired up a second time.
 */
class NoiseControlView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val noiseToggle: AppCompatCheckBox
    private val controls: View
    private val label: TextView
    private val slider: SeekBar

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val horizontal = resources.getDimensionPixelSize(R.dimen.content_horizontal_inset)
        setPaddingRelative(horizontal, resources.getDimensionPixelSize(R.dimen.noise_row_spacing), horizontal, 0)
        LayoutInflater.from(context).inflate(R.layout.noise_control_view, this, true)
        noiseToggle = findViewById(R.id.noiseToggle)
        controls = findViewById(R.id.noiseControls)
        label = findViewById(R.id.noiseLabel)
        slider = findViewById(R.id.noiseSlider)
    }

    /**
     * Every row inflates the same layout, so all of them carry a `noiseSlider` and a `noiseToggle`
     * with the same id, and the window's saved hierarchy state is one array keyed by exactly that:
     * the default dispatch would collapse the rows into a single entry and hand the last one saved
     * back to all of them. A theme or a language change goes through `recreate()`, and the restore
     * lands after [bind] has attached the listeners, so the substituted level and toggle state would be
     * written into each noise's own preferences — over the level the toggle exists to preserve.
     *
     * A row therefore saves itself and nothing below it. It loses no state by that: a level and a
     * toggle state live in the preferences [bind] reads, which is the one place they are kept.
     */
    override fun dispatchSaveInstanceState(container: SparseArray<Parcelable>) {
        dispatchFreezeSelfOnly(container)
    }

    override fun dispatchRestoreInstanceState(container: SparseArray<Parcelable>) {
        dispatchThawSelfOnly(container)
    }

    /**
     * Wires the controls to [noise]'s own preferences and reports what the mix should hear: the
     * stored level while the noise is switched on, silence while it is not.
     *
     * The toggle never writes over the level, so switching a noise back on brings back what it was.
     * The slider drives the toggle the other way round: a level the user sets switches the noise on,
     * and dragging it to zero switches it off — a stored level of zero therefore reads as off here
     * too, whatever the stored flag says, and switching a silent noise on raises it to the quietest
     * audible level. [onVolumeChanged] is called once from here as well, with the stored state, so a
     * caller has nothing left to push afterwards.
     */
    fun bind(noise: NoiseControl, preferences: SharedPreferences, onVolumeChanged: (Float) -> Unit) {
        noiseToggle.contentDescription = noise.name
        // Both before their listeners, so restoring the stored state does not count as a change to save.
        slider.progress = (preferences.getFloat(noise.volumeKey, noise.defaultVolume) * PERCENT_SCALE).toInt()
        // A noise at zero is silent whatever its stored flag says, and the speaker says only what is
        // true: an untouched install has white, and every lab candidate, sitting at 0 %.
        noiseToggle.isChecked = preferences.getBoolean(noise.enabledKey, DEFAULT_NOISE_ENABLED) && slider.progress > 0

        val show = {
            label.text = noise.label(slider.progress)
            controls.alpha = if (noiseToggle.isChecked) 1f else DISABLED_CONTROLS_ALPHA
            onVolumeChanged(if (noiseToggle.isChecked) slider.progress / PERCENT_SCALE else 0f)
        }

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                preferences.edit { putFloat(noise.volumeKey, progress / PERCENT_SCALE) }
                // A level the user just set says what they want to hear, so the toggle follows the
                // slider: off zero switches the noise on, down to zero switches it off. Only for a
                // change the user made — restoring a stored level must switch nothing on by itself.
                if (fromUser) {
                    noiseToggle.isChecked = progress > 0
                }
                show()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Nothing to do: a level is saved on every change, not at the end of a drag.
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Nothing to do, for the same reason.
            }
        })
        noiseToggle.setOnCheckedChangeListener { _, checked ->
            // Switching a noise on has to leave it audible, or the speaker would show a sounding
            // noise over a silent slider — and binding the row again would read it as off anyway.
            if (checked && slider.progress == 0) {
                slider.progress = MIN_AUDIBLE_PROGRESS
            }
            preferences.edit { putBoolean(noise.enabledKey, checked) }
            show()
        }
        show()
    }
}

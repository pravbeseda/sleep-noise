package ru.pravbeseda.sleepnoise.ui

import android.content.Context
import android.content.SharedPreferences
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit
import ru.pravbeseda.sleepnoise.DEFAULT_NOISE_ENABLED
import ru.pravbeseda.sleepnoise.R

/** A seekbar's range as a volume. */
private const val PERCENT_SCALE = 100f

/** What a switched-off noise's label and slider fade to: plainly off, still readable, still movable. */
private const val DISABLED_CONTROLS_ALPHA = 0.5f

/**
 * One noise as its controls need to know it: where its level and its switch are stored, what the
 * switch announces, and how the level reads above the slider.
 */
class NoiseControl(
    val volumeKey: String,
    val enabledKey: String,
    val defaultVolume: Float,
    /** The noise's name, which is what the switch announces to a screen reader. */
    val name: CharSequence,
    /** The text over the slider, given the level as a percentage. */
    val label: (Int) -> CharSequence,
)

/**
 * One noise's settings: a switch that turns the noise off without touching its level, and a slider
 * that sets that level, with the level itself read out above it.
 *
 * Every noise on the screen is one of these — the two the app ships with and each experiment of the
 * noise lab alike — so a new noise gets its switch, its spacing and its persistence by existing
 * rather than by being wired up a second time.
 */
class NoiseControlView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : LinearLayout(context, attrs) {
    private val noiseSwitch: SwitchCompat
    private val controls: View
    private val label: TextView
    private val slider: SeekBar

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val horizontal = resources.getDimensionPixelSize(R.dimen.noise_row_horizontal_padding)
        setPadding(horizontal, resources.getDimensionPixelSize(R.dimen.noise_row_spacing), horizontal, 0)
        LayoutInflater.from(context).inflate(R.layout.noise_control_view, this, true)
        noiseSwitch = findViewById(R.id.noiseSwitch)
        controls = findViewById(R.id.noiseControls)
        label = findViewById(R.id.noiseLabel)
        slider = findViewById(R.id.noiseSlider)
    }

    /**
     * Wires the controls to [noise]'s own preferences and reports what the mix should hear: the
     * stored level while the noise is switched on, silence while it is not.
     *
     * The switch never writes over the level, so switching a noise back on brings back what it was.
     * [onVolumeChanged] is called once from here as well, with the stored state, so a caller has
     * nothing left to push afterwards.
     */
    fun bind(noise: NoiseControl, preferences: SharedPreferences, onVolumeChanged: (Float) -> Unit) {
        noiseSwitch.contentDescription = noise.name
        // Both before their listeners, so restoring the stored state does not count as a change to save.
        slider.progress = (preferences.getFloat(noise.volumeKey, noise.defaultVolume) * PERCENT_SCALE).toInt()
        noiseSwitch.isChecked = preferences.getBoolean(noise.enabledKey, DEFAULT_NOISE_ENABLED)

        val show = {
            label.text = noise.label(slider.progress)
            controls.alpha = if (noiseSwitch.isChecked) 1f else DISABLED_CONTROLS_ALPHA
            onVolumeChanged(if (noiseSwitch.isChecked) slider.progress / PERCENT_SCALE else 0f)
        }

        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                preferences.edit { putFloat(noise.volumeKey, progress / PERCENT_SCALE) }
                show()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                // Nothing to do: a level is saved on every change, not at the end of a drag.
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                // Nothing to do, for the same reason.
            }
        })
        noiseSwitch.setOnCheckedChangeListener { _, checked ->
            preferences.edit { putBoolean(noise.enabledKey, checked) }
            show()
        }
        show()
    }
}

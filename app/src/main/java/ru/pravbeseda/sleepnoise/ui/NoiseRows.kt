package ru.pravbeseda.sleepnoise.ui

import android.content.Context
import android.view.View
import android.widget.LinearLayout
import ru.pravbeseda.sleepnoise.catalog.DEFAULT_LAB_NOISE_VOLUME
import ru.pravbeseda.sleepnoise.catalog.NOISE_LAB_CANDIDATES
import ru.pravbeseda.sleepnoise.catalog.NOISE_LAB_ENABLED
import ru.pravbeseda.sleepnoise.catalog.NoiseLabCandidate
import ru.pravbeseda.sleepnoise.catalog.SHIPPING_NOISES
import ru.pravbeseda.sleepnoise.catalog.ShippingNoise
import ru.pravbeseda.sleepnoise.settings.APP_PREFS
import java.util.Locale

/**
 * One [NoiseControlView] per noise, built from the two registries rather than declared in the layout:
 * the shipping ones from [SHIPPING_NOISES], then the lab's candidates when it is switched on. A new
 * noise is an entry in a registry and nothing else — a row wired up by hand is the mistake the
 * component replaced.
 *
 * Both registries are read straight out of `catalog/` rather than through the service binder: the rows
 * are built in onCreate and the binder does not arrive until after onStart, so a registry behind it
 * would draw nothing.
 */
object NoiseRows {
    /**
     * Returns every row by the key its noise stores its level under, which is how anything outside finds
     * the row belonging to one particular noise. [onVolumeChanged] hears the level only while the noise is
     * switched on; the row decides which it is.
     */
    fun build(
        shippingContainer: LinearLayout,
        labContainer: LinearLayout,
        onVolumeChanged: (volumeKey: String, volume: Float) -> Unit,
    ): Map<String, NoiseControlView> {
        val context = shippingContainer.context
        val preferences = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        val rows = LinkedHashMap<String, NoiseControlView>()

        fun addRow(container: LinearLayout, noise: NoiseControl) {
            // A vertical LinearLayout already gives a child MATCH_PARENT x WRAP_CONTENT, which is what a row wants.
            val row = NoiseControlView(context)
            container.addView(row)
            rows[noise.volumeKey] = row
            row.bind(noise, preferences) { volume -> onVolumeChanged(noise.volumeKey, volume) }
        }

        SHIPPING_NOISES.forEach { noise -> addRow(shippingContainer, shippingNoiseControl(context, noise)) }
        if (NOISE_LAB_ENABLED) {
            labContainer.visibility = View.VISIBLE
            NOISE_LAB_CANDIDATES.forEach { candidate -> addRow(labContainer, labNoiseControl(candidate)) }
        }
        return rows
    }

    private fun shippingNoiseControl(context: Context, noise: ShippingNoise) = NoiseControl(
        noise.volumeKey,
        noise.enabledKey,
        noise.defaultVolume,
        context.getString(noise.nameRes),
    ) { percent -> context.getString(noise.volumeLabelRes, percent) }

    /** The name is developer-facing debug copy on the descriptor, so it is a literal rather than a string resource. */
    private fun labNoiseControl(candidate: NoiseLabCandidate) = NoiseControl(
        candidate.preferenceKey,
        candidate.enabledPreferenceKey,
        DEFAULT_LAB_NOISE_VOLUME,
        candidate.label,
    ) { percent -> String.format(Locale.getDefault(), "%s: %d%%", candidate.label, percent) }
}

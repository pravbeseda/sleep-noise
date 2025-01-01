package ru.pravbeseda.whitenoise

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.SeekBar
import ru.pravbeseda.whitenoise.media.BrownNoiseGenerator
import ru.pravbeseda.whitenoise.media.WhiteNoiseGenerator

const val WHITE_NOISE_VOLUME = "whiteNoiseVolume"
const val BROWN_NOISE_VOLUME = "brownNoiseVolume"

class MainActivity : AppCompatActivity() {
    private val whiteNoiseGenerator = WhiteNoiseGenerator()
    private val brownNoiseGenerator = BrownNoiseGenerator()
    private var isPlaying = false
    private lateinit var preferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferences = getSharedPreferences("NoisePreferences", MODE_PRIVATE)

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
}


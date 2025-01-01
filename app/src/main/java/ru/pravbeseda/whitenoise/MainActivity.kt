package ru.pravbeseda.whitenoise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.SeekBar
import ru.pravbeseda.whitenoise.media.BrownNoiseGenerator
import ru.pravbeseda.whitenoise.media.WhiteNoiseGenerator

class MainActivity : AppCompatActivity() {
    private val whiteNoiseGenerator = WhiteNoiseGenerator()
    private val brownNoiseGenerator = BrownNoiseGenerator()
    private var isPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val playPauseButton: Button = findViewById(R.id.playPauseButton)
        val whiteNoiseVolume: SeekBar = findViewById(R.id.whiteNoiseVolume)
        val brownNoiseVolume: SeekBar = findViewById(R.id.brownNoiseVolume)

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
                whiteNoiseGenerator.setVolume(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        brownNoiseVolume.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                brownNoiseGenerator.setVolume(progress / 100f)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
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


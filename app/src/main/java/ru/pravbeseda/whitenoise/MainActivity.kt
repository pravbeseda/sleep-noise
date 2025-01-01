package ru.pravbeseda.whitenoise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import ru.pravbeseda.whitenoise.media.BrownNoiseGenerator
import ru.pravbeseda.whitenoise.media.WhiteNoiseGenerator

class MainActivity : AppCompatActivity() {
    private val whiteNoiseGenerator = WhiteNoiseGenerator()
    private val brownNoiseGenerator = BrownNoiseGenerator()
    private var currentGenerator: Any? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val startWhiteNoiseButton: Button = findViewById(R.id.startWhiteNoiseButton)
        val startBrownNoiseButton: Button = findViewById(R.id.startBrownNoiseButton)
        val stopNoiseButton: Button = findViewById(R.id.stopNoiseButton)

        startWhiteNoiseButton.setOnClickListener {
            stopCurrentNoise()
            currentGenerator = whiteNoiseGenerator
            whiteNoiseGenerator.startNoise()
        }

        startBrownNoiseButton.setOnClickListener {
            stopCurrentNoise()
            currentGenerator = brownNoiseGenerator
            brownNoiseGenerator.startNoise()
        }

        stopNoiseButton.setOnClickListener {
            stopCurrentNoise()
        }
    }

    private fun stopCurrentNoise() {
        when (currentGenerator) {
            is WhiteNoiseGenerator -> whiteNoiseGenerator.stopNoise()
            is BrownNoiseGenerator -> brownNoiseGenerator.stopNoise()
        }
        currentGenerator = null
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCurrentNoise()
    }
}

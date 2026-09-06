package ru.pravbeseda.sleepnoise.media

import kotlin.math.exp
import kotlin.random.Random

/**
 * A train from inside a carriage: the rumble of the road under it, and the wheels taking the rail joints.
 *
 * The rhythm is the whole source. A bogie meets a joint with two axles a fraction of a second apart, and the
 * next joint is a rail's length away — so the thumps come in pairs, and the pairs come seconds apart. Both
 * intervals carry a little jitter, since a fixed period turns into a metronome the moment the ear finds it.
 *
 * A thump is a burst of the same low band the rumble comes from rather than a click: what reaches the ear
 * through a carriage floor has no top end left, and a bright transient reads as a fault in the playback.
 */
class WheelClatterNoise(private val random: Random = Random.Default) : NoiseSource {
    private val rumble = OnePole(RUMBLE_CUTOFF_HZ)
    private val rails = OnePole(RAIL_CUTOFF_HZ)
    private val thump = OnePole(THUMP_CUTOFF_HZ)

    private var thumpLevel = 0.0
    private var axlesLeftOnThisJoint = AXLES_PER_BOGIE
    private var samplesToNextThump = JOINT_INTERVAL_SAMPLES

    override fun fill(buffer: FloatArray) {
        for (i in buffer.indices) {
            if (samplesToNextThump-- <= 0) strike()
            val white = random.nextDouble(-1.0, 1.0)
            val low = rumble.low(white) * rumble.lowGain
            val rail = (white - rails.low(white)) * rails.highGain
            val struck = thump.low(white) * thump.lowGain * thumpLevel
            thumpLevel *= THUMP_DECAY

            val mixed = (low * RUMBLE_WEIGHT) + (rail * RAIL_WEIGHT) + (struck * THUMP_WEIGHT)
            buffer[i] = (mixed * OUTPUT_GAIN).coerceIn(-1.0, 1.0).toFloat()
        }
    }

    override fun reset() {
        rumble.reset()
        rails.reset()
        thump.reset()
        thumpLevel = 0.0
        axlesLeftOnThisJoint = AXLES_PER_BOGIE
        samplesToNextThump = JOINT_INTERVAL_SAMPLES
    }

    /** One axle over one joint, and the wait until the next — the other axle of this bogie, or the next joint. */
    private fun strike() {
        thumpLevel = random.nextDouble(QUIETEST_THUMP, 1.0)
        axlesLeftOnThisJoint--
        samplesToNextThump = if (axlesLeftOnThisJoint > 0) {
            jittered(AXLE_INTERVAL_SAMPLES, AXLE_JITTER_SAMPLES)
        } else {
            axlesLeftOnThisJoint = AXLES_PER_BOGIE
            jittered(JOINT_INTERVAL_SAMPLES, JOINT_JITTER_SAMPLES)
        }
    }

    private fun jittered(interval: Int, jitter: Int) = interval + random.nextInt(-jitter, jitter)

    private companion object {
        /** The body of the carriage: everything below it is what a train is felt as rather than heard. */
        const val RUMBLE_CUTOFF_HZ = 120.0

        /** The rails themselves, kept quiet: enough to say the train is moving, not enough to hiss. */
        const val RAIL_CUTOFF_HZ = 2_000.0

        /** A thump has no top end by the time it reaches the floor of a carriage. */
        const val THUMP_CUTOFF_HZ = 220.0

        const val RUMBLE_WEIGHT = 1.0
        const val RAIL_WEIGHT = 0.15
        const val THUMP_WEIGHT = 2.8

        const val QUIETEST_THUMP = 0.65

        /** A thump rings for about 90 ms, which is what makes it a knock rather than a click or a drum. */
        const val THUMP_RING_SECONDS = 0.09
        val THUMP_DECAY = exp(-1.0 / (THUMP_RING_SECONDS * SAMPLE_RATE_HZ))

        const val AXLES_PER_BOGIE = 2

        /** The two axles of one bogie over the same joint. */
        val AXLE_INTERVAL_SAMPLES = (0.32 * SAMPLE_RATE_HZ).toInt()
        val AXLE_JITTER_SAMPLES = (0.02 * SAMPLE_RATE_HZ).toInt()

        /** A rail's length at a cruising speed, which is what sets the pace of the whole thing. */
        val JOINT_INTERVAL_SAMPLES = (1.7 * SAMPLE_RATE_HZ).toInt()
        val JOINT_JITTER_SAMPLES = (0.15 * SAMPLE_RATE_HZ).toInt()

        /** Measured RMS of the mix above. */
        const val MIXED_RMS = 0.70

        const val OUTPUT_GAIN = NORMALISED_SOURCE_RMS / MIXED_RMS
    }
}

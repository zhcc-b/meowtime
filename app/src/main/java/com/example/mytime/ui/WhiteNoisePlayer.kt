package com.example.mytime.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

class WhiteNoisePlayer {
    @Volatile
    private var running = false
    @Volatile
    private var mode: SleepSoundMode = SleepSoundMode.RAIN
    private var audioTrack: AudioTrack? = null
    private var writerThread: Thread? = null

    fun start(mode: SleepSoundMode = SleepSoundMode.RAIN) {
        if (running && this.mode == mode) return
        if (running) stop()
        this.mode = mode
        val sampleRate = 22_050
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            minBuffer * 2,
            AudioTrack.MODE_STREAM,
            AudioManager.AUDIO_SESSION_ID_GENERATE
        )
        audioTrack = track
        running = true
        track.play()
        writerThread = thread(start = true, name = "sleep-sound-writer") {
            val buffer = ShortArray(1024)
            val random = Random(System.nanoTime())
            val rainState = RainState()
            val warmNoiseState = WarmNoiseState()
            while (running) {
                when (this.mode) {
                    SleepSoundMode.RAIN -> fillRainBuffer(buffer, random, sampleRate, rainState)
                    SleepSoundMode.WHITE_NOISE -> fillWhiteNoiseBuffer(buffer, random, warmNoiseState)
                }
                track.write(buffer, 0, buffer.size)
            }
        }
    }

    fun stop() {
        running = false
        writerThread?.join(200)
        writerThread = null
        audioTrack?.runCatching {
            pause()
            flush()
            release()
        }
        audioTrack = null
    }

    private fun fillWhiteNoiseBuffer(buffer: ShortArray, random: Random, state: WarmNoiseState) {
        for (i in buffer.indices) {
            val white = random.nextFloat() * 2f - 1f
            state.pink0 = 0.99886f * state.pink0 + white * 0.0555179f
            state.pink1 = 0.99332f * state.pink1 + white * 0.0750759f
            state.pink2 = 0.96900f * state.pink2 + white * 0.1538520f
            state.pink3 = 0.86650f * state.pink3 + white * 0.3104856f
            state.pink4 = 0.55000f * state.pink4 + white * 0.5329522f
            state.pink5 = -0.7616f * state.pink5 - white * 0.0168980f
            val pink = state.pink0 + state.pink1 + state.pink2 + state.pink3 + state.pink4 + state.pink5 + state.pink6 + white * 0.5362f
            state.pink6 = white * 0.115926f
            state.low += (white - state.low) * 0.018f
            val warmNoise = (pink * 0.030f + state.low * 0.055f).coerceIn(-0.22f, 0.22f)
            buffer[i] = (warmNoise * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun fillRainBuffer(
        buffer: ShortArray,
        random: Random,
        sampleRate: Int,
        state: RainState
    ) {
        var filteredRain = state.filteredRain
        var softRain = state.softRain
        var airSheen = state.airSheen
        var rumblePhase = state.rumblePhase
        var dropEnergy = state.dropEnergy
        val rumbleStep = (2f * PI.toFloat() * 46f) / sampleRate
        for (i in buffer.indices) {
            val noise = random.nextFloat() * 2f - 1f
            filteredRain += (noise - filteredRain) * 0.075f
            softRain += (filteredRain - softRain) * 0.018f
            airSheen += (noise - airSheen) * 0.34f
            if (random.nextFloat() < 0.0018f) {
                dropEnergy += 0.42f + random.nextFloat() * 0.38f
            }
            dropEnergy *= 0.965f
            rumblePhase += rumbleStep
            if (rumblePhase > PI.toFloat() * 2f) rumblePhase -= PI.toFloat() * 2f
            val steadyBody = filteredRain * 0.070f + softRain * 0.145f
            val fineMist = airSheen * 0.014f
            val drops = (random.nextFloat() * 2f - 1f) * dropEnergy * 0.038f
            val lowRumble = sin(rumblePhase) * 0.010f
            val sample = ((steadyBody + fineMist + drops + lowRumble) * 1.32f).coerceIn(-0.32f, 0.32f)
            buffer[i] = (sample * Short.MAX_VALUE).toInt().toShort()
        }
        state.filteredRain = filteredRain
        state.softRain = softRain
        state.airSheen = airSheen
        state.rumblePhase = rumblePhase
        state.dropEnergy = dropEnergy
    }

    private class RainState(
        var filteredRain: Float = 0f,
        var softRain: Float = 0f,
        var airSheen: Float = 0f,
        var rumblePhase: Float = 0f,
        var dropEnergy: Float = 0f
    )

    private class WarmNoiseState(
        var pink0: Float = 0f,
        var pink1: Float = 0f,
        var pink2: Float = 0f,
        var pink3: Float = 0f,
        var pink4: Float = 0f,
        var pink5: Float = 0f,
        var pink6: Float = 0f,
        var low: Float = 0f
    )
}

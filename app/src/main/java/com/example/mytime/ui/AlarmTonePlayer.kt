package com.example.mytime.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

class AlarmTonePlayer {
    @Volatile
    private var running = false
    private var audioTrack: AudioTrack? = null
    private var writerThread: Thread? = null

    fun start() {
        if (running) return
        val sampleRate = 22_050
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
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
        writerThread = thread(start = true, name = "daily-alarm-tone") {
            val buffer = ShortArray(1024)
            var sampleCursor = 0L
            while (running) {
                fillAlarmBuffer(buffer, sampleRate, sampleCursor)
                sampleCursor += buffer.size
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

    private fun fillAlarmBuffer(buffer: ShortArray, sampleRate: Int, startSample: Long) {
        val cycleSeconds = 4.2f
        val cycleSamples = (cycleSeconds * sampleRate).toLong()
        for (i in buffer.indices) {
            val cursor = startSample + i
            val elapsedSec = cursor / sampleRate.toFloat()
            val phaseSec = (cursor % cycleSamples) / sampleRate.toFloat()
            val ramp = (elapsedSec / 28f).coerceIn(0.36f, 1f)
            val sample = when {
                phaseSec < 0.46f -> bellTone(phaseSec, 523.25f, sampleRate, cursor, 0.46f)
                phaseSec in 0.66f..1.12f -> bellTone(phaseSec - 0.66f, 659.25f, sampleRate, cursor, 0.46f)
                phaseSec in 1.34f..1.86f -> bellTone(phaseSec - 1.34f, 783.99f, sampleRate, cursor, 0.52f)
                phaseSec in 2.12f..2.42f -> bellTone(phaseSec - 2.12f, 659.25f, sampleRate, cursor, 0.30f) * 0.62f
                else -> 0f
            } * ramp
            buffer[i] = (sample.coerceIn(-0.45f, 0.45f) * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun bellTone(localSec: Float, frequency: Float, sampleRate: Int, sampleCursor: Long, duration: Float): Float {
        val t = sampleCursor / sampleRate.toFloat()
        val attack = (localSec / 0.06f).coerceIn(0f, 1f)
        val release = ((duration - localSec) / duration).coerceIn(0f, 1f)
        val envelope = attack * release * release
        val fundamental = sin(2f * PI.toFloat() * frequency * t)
        val overtone = sin(2f * PI.toFloat() * frequency * 2.01f * t) * 0.22f
        val shimmer = sin(2f * PI.toFloat() * frequency * 3.02f * t) * 0.08f
        return (fundamental + overtone + shimmer) * envelope * 0.46f
    }
}

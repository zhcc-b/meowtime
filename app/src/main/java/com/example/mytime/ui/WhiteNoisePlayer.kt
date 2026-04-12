package com.example.mytime.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.random.Random

class WhiteNoisePlayer {
    @Volatile
    private var running = false
    private var audioTrack: AudioTrack? = null
    private var writerThread: Thread? = null

    fun start() {
        if (running) return
        val sampleRate = 22050
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        if (minBuffer <= 0) return
        val track = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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
        writerThread = thread(start = true, name = "white-noise-writer") {
            val random = Random.Default
            val buffer = ShortArray(1024)
            while (running) {
                for (i in buffer.indices) {
                    val sample = ((random.nextFloat() * 2f - 1f) * Short.MAX_VALUE * 0.09f).toInt()
                    buffer[i] = sample.toShort()
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
}

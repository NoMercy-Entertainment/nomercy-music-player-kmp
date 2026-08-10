// -----------------------------------------------------------------------------
//  Copyright (c) NoMercy Entertainment
//
//  Licensed under the Apache License, Version 2.0. See LICENSE for details.
//
//  SPDX-License-Identifier: Apache-2.0
// -----------------------------------------------------------------------------

package tv.nomercy.player.music.keepalive

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.sin

// Keeps an `AudioTrack` open with a continuous near-inaudible 19 kHz sine so
// an AV receiver or soundbar does not read a signal dropout as "nothing to
// play" and auto-sleep while a viewer is idle.
//
// Ported verbatim from the deprecated app's `KeepAliveAudioController` — the
// two numbers this rests on (19 kHz, amplitude 0.05 / -26 dB) were tuned
// against real hardware there ("0.03 was below the silence-detection
// threshold of several soundbars"), not re-derived. No audio focus is
// requested: the tone must mix into the output route regardless of which
// app holds focus — Spotify, a cast session, anything — never ducking it and
// never being silenced when something else is foreground-audio.
public class AndroidKeepAliveTone(private val context: Context) : KeepAliveTone {

    private val toneBuffer: ShortArray = ShortArray(SAMPLE_RATE) { i ->
        val angle = 2.0 * PI * TONE_FREQ_HZ * i / SAMPLE_RATE
        (sin(angle) * TONE_AMPLITUDE * Short.MAX_VALUE).toInt().toShort()
    }

    @Volatile private var track: AudioTrack? = null
    @Volatile private var writeThread: Thread? = null
    @Volatile private var running = false

    override fun start() {
        if (running) return
        running = true
        openTrackAndStart()
    }

    private fun openTrackAndStart() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        val minBuf = AudioTrack.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        val bufSize = maxOf(minBuf * 4, WRITE_CHUNK * Short.SIZE_BYTES * 2)

        val audioFormat = AudioFormat.Builder()
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
            .build()

        val newTrack = AudioTrack.Builder()
            .setAudioAttributes(audioAttributes)
            .setAudioFormat(audioFormat)
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        if (newTrack.state != AudioTrack.STATE_INITIALIZED) {
            newTrack.release()
            running = false
            return
        }

        newTrack.play()
        track = newTrack
        writeThread = Thread(::writeLoop, "nomercy-keep-alive-tone").also { it.start() }
    }

    override fun stop() {
        if (!running) return
        running = false
        writeThread?.interrupt()
        writeThread = null
        track?.let { t ->
            runCatching {
                t.pause()
                t.flush()
                t.release()
            }
        }
        track = null
    }

    override val isRunning: Boolean get() = running

    private fun writeLoop() {
        var bufPos = 0
        while (running && !Thread.currentThread().isInterrupted) {
            val t = track ?: break
            val toWrite = minOf(WRITE_CHUNK, toneBuffer.size - bufPos)
            val wrote = t.write(toneBuffer, bufPos, toWrite)
            if (wrote < 0) break
            bufPos += wrote
            if (bufPos >= toneBuffer.size) bufPos = 0
        }
    }

    // What output route the tone is actually reaching — expected HDMI/ARC or
    // a Bluetooth A2DP device on a television; `TYPE_BUILTIN_SPEAKER` means
    // it never reached the soundbar this exists for. Exposed rather than
    // only logged, matching the app's own diagnostic intent, so a consumer's
    // own debug surface can show it without re-deriving the enum mapping.
    public fun currentOutputRoute(): List<String> {
        val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return emptyList()
        return manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { device -> routeLabel(device.type) }
    }

    private fun routeLabel(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "BUILTIN_SPEAKER"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "WIRED_HEADPHONES"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "WIRED_HEADSET"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "BLUETOOTH_A2DP"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "BLUETOOTH_SCO"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI_ARC"
        AudioDeviceInfo.TYPE_HDMI_EARC -> "HDMI_EARC"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "LINE_ANALOG"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "LINE_DIGITAL"
        AudioDeviceInfo.TYPE_DOCK -> "DOCK"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB_DEVICE"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB_ACCESSORY"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB_HEADSET"
        AudioDeviceInfo.TYPE_HEARING_AID -> "HEARING_AID"
        AudioDeviceInfo.TYPE_BLE_HEADSET -> "BLE_HEADSET"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE_SPEAKER"
        else -> "TYPE_$type"
    }

    private companion object {
        const val SAMPLE_RATE = 44_100
        const val TONE_FREQ_HZ = 19_000.0
        const val TONE_AMPLITUDE = 0.05 // ~-26 dB — non-zero to hardware, inaudible to humans
        const val WRITE_CHUNK = 2048
    }
}

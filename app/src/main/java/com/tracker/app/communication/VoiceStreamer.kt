package com.tracker.app.communication

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Build

/**
 * Half-duplex push-to-talk audio over the group WebSocket: 8 kHz mono PCM16, 20 ms frames.
 * Sender only transmits while it holds the floor (server enforces). Receiver plays through the
 * active route (speaker, wired or Bluetooth headset via AudioManager communication device).
 * NOTE: uncompressed PCM (~128 kbit/s). Planned upgrade: Opus and/or WebRTC (see docs/ARCHITECTURE.md).
 */
class VoiceStreamer(private val ctx: Context, private val sendFrame: (ByteArray) -> Boolean) {
    private val rate = 8000
    private val frameBytes = rate / 50 * 2
    @Volatile private var recording = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null
    @Volatile var muted = false
    @Volatile var volume = 1.0f
        set(v) { field = v.coerceIn(0f, 1f); track?.setVolume(field) }

    private val audioManager get() = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun routeToBluetoothIfAvailable() {
        if (Build.VERSION.SDK_INT >= 31) {
            audioManager.availableCommunicationDevices.firstOrNull { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }
                ?.let { audioManager.setCommunicationDevice(it) }
        } else @Suppress("DEPRECATION") if (audioManager.isBluetoothScoAvailableOffCall) { audioManager.startBluetoothSco(); audioManager.isBluetoothScoOn = true }
    }

    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the caller before startTalking()
    fun startTalking() {
        if (recording) return
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frameBytes * 4))
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return }
        recording = true
        thread = Thread {
            val buf = ByteArray(frameBytes); rec.startRecording()
            while (recording) { val n = rec.read(buf, 0, buf.size); if (n > 0) sendFrame(buf.copyOf(n)) }
            rec.stop(); rec.release()
        }.also { it.name = "ptt-capture"; it.start() }
    }
    fun stopTalking() { recording = false; thread?.join(300); thread = null }

    fun play(frame: ByteArray) {
        if (muted) return
        val t = track ?: AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(frameBytes * 8).setTransferMode(AudioTrack.MODE_STREAM).build().also { it.setVolume(volume); it.play(); track = it }
        t.write(frame, 0, frame.size)
    }
    fun release() { stopTalking(); track?.release(); track = null }
}

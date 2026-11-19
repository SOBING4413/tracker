package com.tracker.app.communication

import android.annotation.SuppressLint
import android.content.Context
import android.media.*
import android.os.Build

/**
 * Half-duplex push-to-talk audio over the group WebSocket: 8 kHz mono PCM16, 20 ms frames.
 * Sender only transmits while it holds the floor (server enforces). Receiver plays through the
 * active route (speaker, wired or Bluetooth headset via AudioManager communication device).
 * Two ways to talk: hold-to-talk (startTalking/stopTalking) or an always-on mic (startOpenMic): the mic stays open and
 * transmission starts by itself when you speak (voice-activated), then stops after a short silence. The channel is still
 * half-duplex, so an always-on mic only transmits while nobody else is talking (the captain can cut in).
 * NOTE: uncompressed PCM (~128 kbit/s). Planned upgrade: Opus and/or WebRTC (see docs/ARCHITECTURE.md).
 */
class VoiceStreamer(private val ctx: Context, private val sendFrame: (ByteArray) -> Boolean) {
    private val rate = 8000
    private val frameBytes = rate / 50 * 2
    @Volatile private var recording = false
    private var thread: Thread? = null
    @Volatile private var track: AudioTrack? = null
    // Playback runs on its own thread: AudioTrack.write blocks when its buffer is full, and doing that on OkHttp's reader thread
    // delayed pongs and could get the connection dropped. Bounded queue: if we fall behind, the oldest frames are discarded.
    private val playQueue = java.util.concurrent.ThreadPoolExecutor(1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS,
        java.util.concurrent.ArrayBlockingQueue(40), java.util.concurrent.ThreadPoolExecutor.DiscardOldestPolicy())
    @Volatile private var openMic = false
    private var openThread: Thread? = null
    val isOpenMic get() = openMic
    /** 0..1: higher triggers on quieter speech. 0.5 = speech must be ~3x louder than the measured background noise. */
    @Volatile var sensitivity = 0.5f
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

    /**
     * Always-on mic. [onSpeechStart] is called from the audio thread when speech is detected and must return true to start
     * transmitting (false = stay silent: channel busy or muted; it is asked again after 0.5 s). [onSpeechEnd] is called after ~1 s of silence.
     * A rolling noise floor (engine, wind in the helmet) is learned while silent, so steady noise does not trigger the mic.
     */
    @SuppressLint("MissingPermission") // RECORD_AUDIO is checked by the caller
    fun startOpenMic(onSpeechStart: () -> Boolean, onSpeechEnd: () -> Unit) {
        if (openMic || recording) return
        val min = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(min, frameBytes * 4))
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); return }
        openMic = true
        openThread = Thread {
            val buf = ByteArray(frameBytes)
            val preRoll = java.util.ArrayDeque<ByteArray>()   // ~200 ms kept, so the first syllable is not cut off
            var noise = 300.0
            var talking = false; var loud = 0; var quiet = 0; var cooldown = 0; var talkFrames = 0
            rec.startRecording()
            while (openMic) {
                val n = rec.read(buf, 0, buf.size); if (n <= 0) continue
                val frame = buf.copyOf(n); val level = rms(frame, n)
                val thr = maxOf(MIN_SPEECH_RMS, noise * (4.5 - 3.0 * sensitivity.coerceIn(0f, 1f)))
                if (!talking) {
                    if (level < thr) noise = noise * 0.97 + level * 0.03    // learn the background level only while nobody (you) is speaking
                    preRoll.addLast(frame); while (preRoll.size > PRE_ROLL_FRAMES) preRoll.removeFirst()
                    if (cooldown > 0) { cooldown--; continue }
                    loud = if (level > thr) loud + 1 else 0
                    if (loud >= START_FRAMES) {
                        if (onSpeechStart()) { talking = true; quiet = 0; talkFrames = 0; preRoll.forEach { sendFrame(it) }; preRoll.clear() }
                        else { loud = 0; cooldown = 25 }
                    }
                } else {
                    sendFrame(frame); talkFrames++
                    quiet = if (level < thr) quiet + 1 else 0
                    // Safety: never hold the channel for more than 30 s in a row (e.g. constant wind noise), then pause 3 s.
                    if (quiet >= HANG_FRAMES || talkFrames >= MAX_TALK_FRAMES) {
                        talking = false; loud = 0; cooldown = if (talkFrames >= MAX_TALK_FRAMES) 150 else 0; onSpeechEnd()
                    }
                }
            }
            if (talking) onSpeechEnd()
            rec.stop(); rec.release()
        }.also { it.name = "open-mic"; it.start() }
    }
    fun stopOpenMic() { openMic = false; openThread?.join(500); openThread = null }

    private fun rms(b: ByteArray, n: Int): Double {
        val samples = n / 2; if (samples == 0) return 0.0
        var sum = 0.0; var i = 0
        while (i + 1 < n) { val v = ((b[i].toInt() and 0xFF) or (b[i + 1].toInt() shl 8)).toShort().toInt(); sum += v.toDouble() * v; i += 2 }
        return Math.sqrt(sum / samples)
    }
    private companion object {
        const val MIN_SPEECH_RMS = 600.0     // never trigger below this level, however quiet the surroundings
        const val START_FRAMES = 3           // 60 ms of sound above the threshold = speech
        const val HANG_FRAMES = 50           // 1 s of quiet = speech ended
        const val PRE_ROLL_FRAMES = 10
        const val MAX_TALK_FRAMES = 1500     // 30 s
    }

    fun play(frame: ByteArray) {
        if (muted) return
        runCatching { playQueue.execute { runCatching { writeFrame(frame) } } }   // ignore: rejected after release(), or track released mid-write
    }

    private fun writeFrame(frame: ByteArray) {
        val t = track ?: AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(frameBytes * 8).setTransferMode(AudioTrack.MODE_STREAM).build().also { it.setVolume(volume); it.play(); track = it }
        t.write(frame, 0, frame.size)
    }
    fun release() { stopOpenMic(); stopTalking(); playQueue.shutdownNow(); track?.release(); track = null }
}

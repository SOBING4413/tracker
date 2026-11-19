package com.tracker.app.network

import android.os.Handler
import android.os.Looper
import com.tracker.app.BuildConfig
import com.tracker.app.utils.Prefs
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min

/** WebSocket client with exponential-backoff auto-reconnect and a small outbound queue while offline. */
class RealtimeClient(private val groupId: String, private val onMessage: (JSONObject) -> Unit, private val onState: (Boolean) -> Unit, private val onBinary: ((ByteArray) -> Unit)? = null) {
    private val http = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())
    private var ws: WebSocket? = null
    private var retry = 0
    @Volatile private var stopped = false
    @Volatile var connected = false; private set

    fun start() { stopped = false; open() }
    fun stop() { stopped = true; ws?.close(1000, null); ws = null }

    private fun open() {
        val url = BuildConfig.BASE_URL.replaceFirst("http", "ws") + "/ws?token=${Prefs.token}&group=$groupId"
        ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(w: WebSocket, r: Response) { retry = 0; connected = true; main.post { onState(true) } }
            override fun onMessage(w: WebSocket, text: String) { runCatching { JSONObject(text) }.getOrNull()?.let { m -> main.post { onMessage(m) } } }
            override fun onMessage(w: WebSocket, bytes: okio.ByteString) { onBinary?.invoke(bytes.toByteArray()) }
            override fun onClosed(w: WebSocket, code: Int, reason: String) = lost()
            override fun onFailure(w: WebSocket, t: Throwable, r: Response?) = lost()
        })
    }
    private fun lost() {
        connected = false; main.post { onState(false) }
        if (stopped) return
        val delay = min(30_000L, 1000L shl min(retry++, 5))
        main.postDelayed({ if (!stopped) open() }, delay)
    }
    fun sendBytes(b: ByteArray): Boolean = ws?.takeIf { connected }?.send(okio.ByteString.of(*b)) ?: false
    fun send(o: JSONObject): Boolean = ws?.takeIf { connected }?.send(o.toString()) ?: false
}

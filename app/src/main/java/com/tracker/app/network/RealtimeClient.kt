package com.tracker.app.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Handler
import android.os.Looper
import com.tracker.app.BuildConfig
import com.tracker.app.utils.Prefs
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.min
import kotlin.random.Random

/**
 * WebSocket client with auto-reconnect.
 *
 * Reliability notes:
 *  - All state is touched on the main thread only; every OkHttp callback is posted there and ignored
 *    if it belongs to a socket that is no longer the current one (stale callbacks used to flip the
 *    state to "offline" and spawn extra reconnects while a healthy socket was already open).
 *  - Reconnects immediately when the default network changes (Wi-Fi <-> mobile data, cell handover),
 *    instead of waiting for the dead socket to time out.
 *  - Backoff is capped at 8 s with jitter (was up to 30 s).
 *  - Replies to the server's close frame so the socket is really released.
 */
class RealtimeClient(
    ctx: Context,
    private val groupId: String,
    private val onMessage: (JSONObject) -> Unit,
    private val onState: (Boolean) -> Unit,
    private val onBinary: ((ByteArray) -> Unit)? = null,
) {
    private val http = OkHttpClient.Builder().pingInterval(10, TimeUnit.SECONDS).build()
    private val main = Handler(Looper.getMainLooper())
    private val cm = ctx.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    @Volatile private var ws: WebSocket? = null
    private var retry = 0
    private var lastOpenAt = 0L
    private var netCbRegistered = false
    @Volatile private var stopped = true
    @Volatile var connected = false; private set

    private val reconnectTask = Runnable { if (!stopped) open() }

    private val netCb = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            main.post {
                // Also fires once on registration; ignore if we just opened a socket.
                if (stopped || System.currentTimeMillis() - lastOpenAt < 2_000) return@post
                retry = 0; open()
            }
        }
        override fun onLost(network: Network) { main.post { if (!stopped) ws?.cancel() } }
    }

    fun start() { main.post { if (!stopped) return@post; stopped = false; registerNetwork(); open() } }

    fun stop() {
        main.post {
            stopped = true
            main.removeCallbacks(reconnectTask)
            unregisterNetwork()
            ws?.close(1000, null); ws = null; connected = false
        }
    }

    private fun registerNetwork() {
        if (netCbRegistered) return
        netCbRegistered = runCatching { cm?.registerDefaultNetworkCallback(netCb) }.isSuccess
    }
    private fun unregisterNetwork() {
        if (!netCbRegistered) return
        runCatching { cm?.unregisterNetworkCallback(netCb) }; netCbRegistered = false
    }

    private fun open() {
        main.removeCallbacks(reconnectTask)
        ws?.cancel()   // drop any old/half-dead socket; its late callbacks are ignored (w !== ws)
        lastOpenAt = System.currentTimeMillis()
        val url = BuildConfig.BASE_URL.replaceFirst("http", "ws") + "/ws?token=${Prefs.token}&group=$groupId"
        ws = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(w: WebSocket, r: Response) { main.post {
                if (w !== ws) return@post
                retry = 0; connected = true; onState(true)
            } }
            override fun onMessage(w: WebSocket, text: String) {
                val m = runCatching { JSONObject(text) }.getOrNull() ?: return
                main.post { if (w === ws) onMessage(m) }
            }
            override fun onMessage(w: WebSocket, bytes: okio.ByteString) { if (w === ws) onBinary?.invoke(bytes.toByteArray()) }
            override fun onClosing(w: WebSocket, code: Int, reason: String) { w.close(code, reason) }   // complete the close handshake
            override fun onClosed(w: WebSocket, code: Int, reason: String) { main.post { lost(w, code) } }
            override fun onFailure(w: WebSocket, t: Throwable, r: Response?) { main.post { lost(w, -1) } }
        })
    }

    private fun lost(w: WebSocket, code: Int) {
        if (w !== ws) return            // stale socket, ignore
        ws = null
        if (connected) { connected = false; onState(false) }
        if (stopped) return
        // 4003 = token/group rejected, 4000 = replaced by another login of the same account:
        // retrying fast is pointless (and two devices would kick each other in a loop).
        val base = if (code == 4003 || code == 4000) 30_000L else min(8_000L, 500L shl min(retry, 4))
        retry++
        main.postDelayed(reconnectTask, base / 2 + Random.nextLong(base / 2 + 1))
    }

    fun sendBytes(b: ByteArray): Boolean = ws?.takeIf { connected }?.send(okio.ByteString.of(*b)) ?: false
    fun send(o: JSONObject): Boolean = ws?.takeIf { connected }?.send(o.toString()) ?: false
}

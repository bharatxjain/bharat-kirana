package com.kks.bharatkirana.data.supabase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal Supabase Realtime (Phoenix) client over an OkHttp WebSocket.
 *
 *  - Connects to the project's `/realtime/v1/websocket` endpoint.
 *  - Joins `realtime:public:orders` and `realtime:public:notifications` channels.
 *  - Emits every `postgres_changes` payload on [changes] as raw JSON so callers
 *    (e.g. GroceryViewModel) can decide how to merge into local state.
 *  - Sends a 30-second heartbeat as required by the Phoenix protocol.
 *  - Auto-reconnects on failure with a 5-second delay.
 *
 * RLS still applies: without an access token, the socket sees only what the
 * anon key can see (nothing, for orders/notifications). Call [setAccessToken]
 * after login so the user's JWT is applied to the subscription.
 */
class SupabaseRealtimeClient(
  private val httpClient: OkHttpClient = OkHttpClient()
) {

  private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
  private var webSocket: WebSocket? = null
  private var heartbeatJob: Job? = null
  private var accessToken: String? = null
  private var refCounter = 0
  private var wantsConnection = false
  private var isConnected = false

  private val _changes = MutableSharedFlow<RealtimeChange>(extraBufferCapacity = 32)
  val changes: SharedFlow<RealtimeChange> = _changes.asSharedFlow()

  data class RealtimeChange(
    val table: String,
    val type: String,         // INSERT / UPDATE / DELETE
    val record: JSONObject?,
    val oldRecord: JSONObject?
  )

  fun connect(accessToken: String? = null) {
    this.accessToken = accessToken
    wantsConnection = true
    open()
  }

  fun setAccessToken(token: String?) {
    accessToken = token
    val ws = webSocket ?: return
    token ?: return
    if (!isConnected) return
    val msg = JSONObject().apply {
      put("topic", "realtime:public:orders")
      put("event", "access_token")
      put("payload", JSONObject().put("access_token", token))
      put("ref", nextRef())
    }
    ws.send(msg.toString())
  }

  fun disconnect() {
    wantsConnection = false
    heartbeatJob?.cancel()
    heartbeatJob = null
    webSocket?.close(1000, "bye")
    webSocket = null
    isConnected = false
  }

  private fun open() {
    val wsUrl = SupabaseConfig.PROJECT_URL
      .replace("https://", "wss://")
      .replace("http://", "ws://")
    val url = "$wsUrl/realtime/v1/websocket?apikey=${SupabaseConfig.API_KEY}&vsn=1.0.0"
    val request = Request.Builder().url(url).build()
    webSocket?.close(1000, "reopen")
    webSocket = httpClient.newWebSocket(request, listener)
  }

  private fun nextRef(): String = (++refCounter).toString()

  private val listener = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      isConnected = true
      joinChannel(webSocket, "realtime:public:orders", "orders")
      joinChannel(webSocket, "realtime:public:notifications", "notifications")
      accessToken?.let { token -> pushAccessToken(webSocket, token) }
      startHeartbeat(webSocket)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      try {
        val msg = JSONObject(text)
        val event = msg.optString("event")
        if (event != "postgres_changes") return
        val payload = msg.optJSONObject("payload") ?: return
        val data = payload.optJSONObject("data") ?: return
        val table = data.optString("table")
        val type = data.optString("type")
        if (table.isBlank() || type.isBlank()) return
        val record = data.optJSONObject("record")
        val oldRecord = data.optJSONObject("old_record")
        scope.launch { _changes.emit(RealtimeChange(table, type, record, oldRecord)) }
      } catch (_: Exception) { }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      isConnected = false
      heartbeatJob?.cancel()
      heartbeatJob = null
      if (wantsConnection) {
        scope.launch {
          delay(5_000)
          if (wantsConnection) open()
        }
      }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      isConnected = false
      heartbeatJob?.cancel()
      heartbeatJob = null
    }
  }

  private fun joinChannel(webSocket: WebSocket, topic: String, table: String) {
    val join = JSONObject().apply {
      put("topic", topic)
      put("event", "phx_join")
      put("payload", JSONObject().apply {
        put("config", JSONObject().apply {
          put(
            "postgres_changes",
            JSONArray().put(
              JSONObject().apply {
                put("event", "*")
                put("schema", "public")
                put("table", table)
              }
            )
          )
        })
      })
      put("ref", nextRef())
    }
    webSocket.send(join.toString())
  }

  private fun pushAccessToken(webSocket: WebSocket, token: String) {
    val msg = JSONObject().apply {
      put("topic", "realtime:public:orders")
      put("event", "access_token")
      put("payload", JSONObject().put("access_token", token))
      put("ref", nextRef())
    }
    webSocket.send(msg.toString())
  }

  private fun startHeartbeat(webSocket: WebSocket) {
    heartbeatJob?.cancel()
    heartbeatJob = scope.launch {
      while (isActive) {
        delay(30_000)
        val hb = JSONObject().apply {
          put("topic", "phoenix")
          put("event", "heartbeat")
          put("payload", JSONObject())
          put("ref", "hb-${System.currentTimeMillis()}")
        }
        try { webSocket.send(hb.toString()) } catch (_: Exception) { }
      }
    }
  }
}

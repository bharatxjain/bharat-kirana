package com.kks.bharatkirana.data.supabase

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
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
  @Volatile private var webSocket: WebSocket? = null
  private var heartbeatJob: Job? = null
  private var reconnectJob: Job? = null
  @Volatile private var accessToken: String? = null
  private var refCounter = 0
  @Volatile private var wantsConnection = false
  @Volatile private var generation = 0

  private val _connected = MutableStateFlow(false)
  val connected: StateFlow<Boolean> = _connected.asStateFlow()

  private val topics = listOf(
    "realtime:public:orders" to "orders",
    "realtime:public:notifications" to "notifications",
    "realtime:public:products" to "products"
  )

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
    if (!_connected.value) return
    for ((topic, _) in topics) {
      val msg = JSONObject().apply {
        put("topic", topic)
        put("event", "access_token")
        put("payload", JSONObject().put("access_token", token))
        put("ref", nextRef())
      }
      ws.send(msg.toString())
    }
  }

  fun disconnect() {
    wantsConnection = false
    generation++
    reconnectJob?.cancel()
    reconnectJob = null
    heartbeatJob?.cancel()
    heartbeatJob = null
    webSocket?.close(1000, "bye")
    webSocket = null
    _connected.value = false
  }

  private fun open() {
    val wsUrl = SupabaseConfig.PROJECT_URL
      .replace("https://", "wss://")
      .replace("http://", "ws://")
    val url = "$wsUrl/realtime/v1/websocket?apikey=${SupabaseConfig.API_KEY}&vsn=1.0.0"
    val request = Request.Builder().url(url).build()
    val gen = ++generation
    webSocket?.close(1000, "reopen")
    webSocket = httpClient.newWebSocket(request, SocketListener(gen))
  }

  private fun nextRef(): String = (++refCounter).toString()

  // Any drop — network failure or a clean close from the server — is followed
  // by a reconnect while the app still wants one.
  private fun onSocketLost(gen: Int) {
    if (gen != generation) return
    _connected.value = false
    heartbeatJob?.cancel()
    heartbeatJob = null
    if (!wantsConnection || reconnectJob?.isActive == true) return
    reconnectJob = scope.launch {
      delay(5_000)
      if (wantsConnection) open()
    }
  }

  // One listener per socket; `gen` tells callbacks of a replaced socket apart
  // from the live one (onOpen can fire before newWebSocket() even returns).
  private inner class SocketListener(private val gen: Int) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      if (gen != generation) return
      // The JWT must be attached to the phx_join payload itself so RLS applies to
      // the subscription from the start — sending it as a follow-up "access_token"
      // event afterward (as this used to do, and only for the orders topic) left
      // both channels running as the anon role, so no postgres_changes ever passed
      // row-level security and neither orders updates nor notification inserts
      // ever reached the client. The products channel lets a customer browsing
      // the catalog see a shopkeeper's stock toggle without a pull-to-refresh.
      for ((topic, table) in topics) joinChannel(webSocket, topic, table)
      startHeartbeat(webSocket)
      _connected.value = true
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
      onSocketLost(gen)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
      onSocketLost(gen)
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
        accessToken?.let { put("access_token", it) }
      })
      put("ref", nextRef())
    }
    webSocket.send(join.toString())
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

@file:Suppress("MemberVisibilityCanBePrivate")

package org.mesagisto.client

import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.mesagisto.client.data.Packet
import org.mesagisto.client.utils.ControlFlow
import java.net.URI
import java.nio.ByteBuffer

private val WsScope = CoroutineScope(Dispatchers.Default)

class WebSocketSession(
  val serverName: String,
  val serverURI: URI,
  var fut: CompletableDeferred<WebSocketSession>?,
  var reconnect: Boolean = false,
  private var closed: Boolean = false
) : WebSocketClient(serverURI) {

  companion object {
    suspend fun asyncConnect(
      server: String,
      serverURI: URI,
      timeout: Long,
      reconnect: Boolean = false
    ): Result<WebSocketSession> {
      val fut = CompletableDeferred<WebSocketSession>()
      val ws = WebSocketSession(server, serverURI, fut, reconnect)
      ws.connect()
      ws.connectionLostTimeout = 30
      return runCatching {
        withTimeout(timeout) {
          fut.await()
        }
      }.onFailure {
        ws.close()
      }
    }
  }
  override fun onOpen(handshakedata: ServerHandshake) {
    val fut = this.fut
    if (fut != null) {
      fut.complete(this)
      this.fut = null
    }
  }

  override fun onClose(code: Int, reason: String, remote: Boolean) = WsScope.launch fn@{
    if (reason.isEmpty()) {
      Logger.info { "Websocket closed code $code" }
    } else {
      Logger.info { "Websocket closed code $code info: $reason }" }
    }

    fut?.completeExceptionally(IllegalStateException(reason))
    if (closed || reconnect) return@fn
    var retryTimes = 1
    while (true) {
      Logger.info { "Trying to connect WS server $serverName, retry times $retryTimes" }
      if (Server.reconnect(serverName, serverURI).isSuccess) break
      retryTimes += 1
      delay(10_000)
      continue
    }
  }.let { }

  override fun onMessage(message: String) {
    Logger.warn { "received text message: $message" }
  }

  override fun onMessage(bin: ByteBuffer) = WsScope.launch fn@{
    runCatching {
      val packet = Packet.fromCbor(bin.array())
        .onFailure { Logger.warn { "packet de exception ${it.message}" } }
        .getOrNull() ?: return@fn
      val res = Server.packetHandler.invoke(packet)
        .onFailure { Logger.error(it) }
        .getOrNull() ?: return@fn
      when (res) {
        is ControlFlow.Break -> {
          Server.handleRestPkt(res.value)
        }
        is ControlFlow.Continue -> {}
      }
    }.onFailure {
      Logger.error(it)
    }
  }.let { }

  override fun onError(ex: Exception) {
    Logger.error { "An error occurred in websocket:$ex" }
  }

  override fun close() {
    closed = true
    super.close()
  }
}

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
  var fut: CompletableDeferred<WebSocketSession>?
) : WebSocketClient(serverURI) {

  companion object {
    suspend fun asyncConnect(
      server: String,
      serverURI: URI,
      timeout: Long
    ): Result<WebSocketSession> {
      val fut = CompletableDeferred<WebSocketSession>()
      val ws = WebSocketSession(server, serverURI, fut)
      ws.connect()
      return runCatching {
        withTimeout(timeout) {
          fut.await()
        }
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

  override fun onClose(code: Int, reason: String, remote: Boolean) {
    if (code == 2000) return
    println("closed with exit code $code additional info: $reason")
    runBlocking {
      if (code == -1) {
        fut?.completeExceptionally(IllegalStateException(reason))
        delay(2000)
      }
      Server.reconnect(serverName, serverURI)
    }
  }

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
    Logger.error { "an error occurred:$ex" }
  }
}

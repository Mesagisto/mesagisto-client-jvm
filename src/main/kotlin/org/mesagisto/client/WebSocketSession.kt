@file:Suppress("MemberVisibilityCanBePrivate")

package org.mesagisto.client

import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import org.mesagisto.client.data.Packet
import org.mesagisto.client.utils.ControlFlow
import java.net.URI
import java.nio.ByteBuffer

class WebSocketSession(
  val server: String,
  val serverURI: URI
) : WebSocketClient(serverURI), CoroutineScope by CoroutineScope(Job()) {
  init {
    connect()
  }

  override fun onOpen(handshakedata: ServerHandshake) {
    println("new connection opened")
  }

  override fun onClose(code: Int, reason: String, remote: Boolean) {
    cancel()
    println("closed with exit code $code additional info: $reason")
  }

  override fun onMessage(message: String) {
    println("received message: $message")
  }

  override fun onMessage(bin: ByteBuffer) = launch fn@{
    runCatching {
      val packet = Packet.fromCbor(bin.array()).onFailure { Logger.warn { "packet de exception" } }.getOrNull() ?: return@fn
      val res = Server.packetHandler.invoke(packet).onFailure { Logger.error(it) }.getOrNull() ?: return@fn
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
    System.err.println("an error occurred:$ex")
  }
}

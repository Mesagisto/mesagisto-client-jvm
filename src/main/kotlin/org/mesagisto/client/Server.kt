package org.mesagisto.client

import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import org.mesagisto.client.data.Inbox
import org.mesagisto.client.data.Packet
import org.mesagisto.client.utils.ControlFlow
import org.mesagisto.client.utils.UUIDv5
import java.io.Closeable
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentHashMap

typealias PackHandler = suspend (Packet) -> Result<ControlFlow<Packet, Unit>>

object Server : Closeable {
  private val remoteEndpoints = ConcurrentHashMap<String, WebSocketSession>()
  lateinit var packetHandler: PackHandler
  private val inbox = ConcurrentHashMap<UUID, CompletableDeferred<Packet>>()
  val roomMap = ConcurrentHashMap<String, UUID>()
  suspend fun init(remotes: Map<String, String>) = withCatch(Dispatchers.Default) {
    val endpoints = remotes.map {
      val serverName = it.key
      val serverAddress = it.value
      async {
        runCatching {
          Logger.info { "connecting" }
          val ws = withTimeout(7000) {
            WebSocketSession.asyncConnect(serverName, URI(serverAddress)).await()
          }
          serverName to ws
        }.onFailure { e ->
          Logger.error(e)
        }.onSuccess {
          Logger.info { "Successfully connected to $serverName $serverAddress" }
        }.getOrNull()
      }
    }.awaitAll().filterNotNull()
    remoteEndpoints.putAll(endpoints)
  }

  suspend fun handleRestPkt(pkt: Packet) {
    when (val pktInbox = pkt.inbox) {
      is Inbox.Request -> {
        // TODO
        println("todo ${pkt.inbox}")
      }
      is Inbox.Respond -> {
        val fut = inbox.remove(pktInbox.id) ?: return
        fut.complete(pkt)
      }
      else -> {}
    }
  }

  suspend fun handleEndpointClose(name: String, uri: URI) = withContext(Dispatchers.Default) {
    val formerEndpoint = remoteEndpoints.remove(name)
    val latter = WebSocketSession.asyncConnect(name, uri).await()
    val conflict = remoteEndpoints.put(name, latter) ?: return@withContext
    conflict.close(2000)
  }
  fun roomId(roomAddress: String): UUID = roomMap.getOrPut(roomAddress) {
    val uniqueAddress = Cipher.uniqueAddress(roomAddress)
    UUIDv5.fromString(uniqueAddress)
  }

  override fun close() {
    throw NotImplementedError()
  }

  suspend fun send(
    content: Packet,
    serverName: String
  ) {
    val bytes = content.toCbor()
    val remote = remoteEndpoints[serverName] ?: run {
      Logger.error { "wtf" }
      return
    }
    withContext(Dispatchers.IO) {
      remote.send(bytes)
    }
  }

  suspend fun unsub(
    room: UUID,
    serverName: String
  ) {
    val pkt = Packet.newUnsub(room)
    send(pkt, serverName)
  }
  suspend fun sub(
    room: UUID,
    serverName: String
  ) {
    val pkt = Packet.newSub(room)
    send(pkt, serverName)
  }

  suspend fun request(
    pkt: Packet,
    serverName: String
  ): Result<Packet> = withContext(Dispatchers.Default) {
    runCatching {
      val inbox = pkt.inbox
      if (inbox == null) {
        pkt.inbox = Inbox.Request()
      }
      val fut = CompletableDeferred<Packet>()
      this@Server.inbox[(pkt.inbox as Inbox.Request).id] = fut
      launch { send(pkt, serverName) }
      fut.await()
    }
  }
  suspend fun respond(
    pkt: Packet,
    serverName: String,
    inbox: UUID
  ): Result<Unit> = withCatch(Dispatchers.Default) {
    pkt.inbox = Inbox.Respond(inbox)
    send(pkt, serverName)
  }
}

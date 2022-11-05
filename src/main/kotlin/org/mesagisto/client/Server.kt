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
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.locks.Lock
import java.util.concurrent.locks.ReentrantLock

typealias PackHandler = suspend (Packet) -> Result<ControlFlow<Packet, Unit>>
typealias ServerName = String

object Server : Closeable {
  private val remoteEndpoints = ConcurrentHashMap<String, WebSocketSession>()
  lateinit var packetHandler: PackHandler
  lateinit var remotes: Map<String, String>
  private val inbox = ConcurrentHashMap<UUID, CompletableDeferred<Packet>>()
  val roomMap = ConcurrentHashMap<String, UUID>()
  suspend fun init(remotes: Map<String, String>) = withCatch(Dispatchers.Default) {
    this@Server.remotes = remotes
    val endpoints = remotes.map {
      val serverName = it.key
      val serverAddress = it.value
      async {
        Logger.info { "Connecting to websocket" }
        val conn = WebSocketSession.asyncConnect(
          serverName,
          URI(serverAddress),
          7000
        ).onFailure { e -> Logger.error(e) }
          .onSuccess { Logger.info { "Successfully connected to $serverName $serverAddress" } }
          .getOrNull() ?: return@async null
        serverName to conn
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

  private val reconnectPoison = ConcurrentHashMap<ServerName, Lock>()
  suspend fun reconnect(name: ServerName, uri: URI) = withContext(Dispatchers.Default) {
    Logger.info { "Reconnecting to $name $uri" }
    val lock = reconnectPoison.getOrPut(name) { ReentrantLock() }
    if (!lock.tryLock()) return@withContext

    remoteEndpoints.remove(name)
    WebSocketSession.asyncConnect(name, uri, 7000)
      .onSuccess {
        val conflict = remoteEndpoints.put(name, it) ?: return@withContext
        Logger.info { "Reconnect successfully" }
        conflict.close(2000)
        lock.unlock()
        subs.forEach { sub ->
          sub.value.forEach { uuid ->
            Logger.debug { "ReSub on ${sub.key} $uuid" }
            val pkt = Packet.newSub(uuid)
            send(pkt, sub.key)
          }
        }
        reconnectPoison.remove(name)
      }.onFailure {
        Logger.warn { "Reconnect to $name failed" }
        lock.unlock()
        Logger.error(it)
      }
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
      val uri = this.remotes[serverName] ?: return
      reconnect(serverName, URI(uri))
      return
    }
    withContext(Dispatchers.IO) {
      remote.send(bytes)
    }
  }

  private val subs = ConcurrentHashMap<ServerName, CopyOnWriteArraySet<UUID>>()

  suspend fun sub(
    room: UUID,
    serverName: String
  ) {
    Logger.debug { "Sub on $serverName $room" }
    val entry = subs.getOrPut(serverName) { CopyOnWriteArraySet() }
    entry.add(room)
    val pkt = Packet.newSub(room)
    send(pkt, serverName)
  }

  suspend fun unsub(
    room: UUID,
    serverName: String
  ) {
    val entry = subs.getOrPut(serverName) { CopyOnWriteArraySet() }
    entry.remove(room)
    val pkt = Packet.newUnsub(room)
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

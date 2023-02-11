@file:Suppress("MemberVisibilityCanBePrivate")

package org.mesagisto.client

import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.sync.Mutex
import org.mesagisto.client.data.Inbox
import org.mesagisto.client.data.Packet
import org.mesagisto.client.utils.ControlFlow
import org.mesagisto.client.utils.UUIDv5
import java.io.Closeable
import java.net.URI
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArraySet

typealias PackHandler = suspend (Packet) -> Result<ControlFlow<Packet, Unit>>
typealias ServerName = String

object Server : Closeable {
  private val remoteEndpoints = ConcurrentHashMap<String, WebSocketSession>()
  lateinit var packetHandler: PackHandler
  lateinit var remotes: Map<String, String>
  private val inbox = ConcurrentHashMap<UUID, CompletableDeferred<Packet>>()
  private val reconnectPoison = ConcurrentHashMap<ServerName, Mutex>()
  val roomMap = ConcurrentHashMap<String, UUID>()
  var sameSideDeliver = true
  suspend fun init(remotes: MutableMap<String, String>, sameSideDeliver: Boolean) = withCatch(Dispatchers.Default) {
    this@Server.remotes = remotes
    val override = System.getenv("MESAGISTO_OVERRIDE_CENTER")
    if (override.isNotBlank()) {
      remotes["mesagisto"] = override
    } else {
      remotes["mesagisto"] = "wss://mesagisto.itsusinn.site"
    }
    this@Server.sameSideDeliver = sameSideDeliver
    val endpoints = remotes.map {
      val serverName = it.key
      val serverAddress = it.value
      async {
        Logger.info { "Connecting to websocket $serverName $serverAddress" }
        val conn = WebSocketSession.asyncConnect(
          serverName,
          URI(serverAddress),
          15_000
        ).onFailure { e -> Logger.error(e) }
          .onSuccess { Logger.info { "Successfully connected to $serverName" } }
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

  suspend fun reconnect(name: ServerName, uri: URI): Result<WebSocketSession> {
    Logger.info { "Reconnecting to $name $uri" }
    val lock = reconnectPoison.getOrPut(name) { Mutex() }
    if (!lock.tryLock()) return Result.failure(IllegalStateException())

    remoteEndpoints.remove(name)
    return WebSocketSession.asyncConnect(name, uri, 30_000, true)
      .onSuccess {
        remoteEndpoints.put(name, it)?.close(2000)
        it.reconnect = false
        Logger.info { "Reconnect successfully" }
        subs.forEach { sub ->
          sub.value.forEach { uuid ->
            Logger.debug { "ReSub on ${sub.key} $uuid" }
            val pkt = Packet.newSub(uuid)
            send(pkt, sub.key)
          }
        }
        lock.unlock()
        reconnectPoison.remove(name)
      }.onFailure {
        Logger.warn { "Reconnect to $name failed ${it.message}" }
        lock.unlock()
      }
  }
  fun roomId(roomAddress: String): UUID = roomMap.getOrPut(roomAddress) {
    val uniqueAddress = Cipher.uniqueAddress(roomAddress)
    UUIDv5.fromString(uniqueAddress)
  }

  override fun close() {
    for (endpoint in remoteEndpoints) {
      runCatching {
        endpoint.value.close()
      }.onFailure {
        it.printStackTrace()
      }
    }
  }

  suspend fun send(
    content: Packet,
    serverName: String
  ) = withContext(Dispatchers.Default) fn@{
    if (sameSideDeliver) {
      launch {
        packetHandler.invoke(content)
      }
    }
    val bytes = content.toCbor()
    val remote = remoteEndpoints[serverName] ?: run {
      val uri = this@Server.remotes[serverName] ?: return@fn
      reconnect(serverName, URI(uri))
      return@fn
    }
    launch {
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

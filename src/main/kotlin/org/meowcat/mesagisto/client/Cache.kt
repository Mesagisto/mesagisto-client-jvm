@file:Suppress("NOTHING_TO_INLINE", "unused", "MemberVisibilityCanBePrivate")
package org.meowcat.mesagisto.client

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import org.meowcat.mesagisto.client.data.* // ktlint-disable no-wildcard-imports
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.* // ktlint-disable no-wildcard-imports
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.* // ktlint-disable no-wildcard-imports

object Cache : CoroutineScope {
  private val httpProxy by lazy {
    val uri = URI("http://127.0.0.1:7890")
    val type = when (uri.scheme) {
      "http" -> Proxy.Type.HTTP
      "socks" -> Proxy.Type.SOCKS
      else -> Proxy.Type.DIRECT
    }
    Proxy(type, InetSocketAddress(uri.host, uri.port))
  }

  fun get(name: String): Result<Option<Path>> = runCatching {
    val file = Res.path(name)
    if (file.exists()) Some(file) else None
  }
  suspend fun file(id: String, url: String?, address: String): Result<Path> {
    return if (url == null) {
      fileById(id, address)
    } else {
      fileByUrl(id, url)
    }
  }
  suspend fun fileById(
    id: String,
    address: String,
  ): Result<Path> = runCatching call@{
    Logger.debug { "Cache by id $id" }
    val path = Res.path(id)
    if (path.exists()) {
      Logger.trace { "File exists,return the path" }
      return@call path
    }
    val tmpPath = Res.tmpPath(id)

    if (tmpPath.exists()) return@call suspendCoroutine { res ->
      Logger.trace { "TmpFile exists,waiting for the file downloading" }
      Res.waitFor(id) { res.resume(it) }
    }

    Logger.trace { "TmpFile dont exists,requesting url" }
    val packet = Event(EventType.RequestImage(id)).toCipherPacket()
    val response = Server.request(address, packet, Server.LibHeader).getOrThrow()

    return@call when (val rPacket = Packet.fromCbor(response.data).getOrThrow()) {
      is Either.Right -> {
        val event = rPacket.value.data
        if (event !is EventType.RespondImage) error("Not correct response")
        fileByUrl(event.id, event.url).getOrThrow()
      }
      is Either.Left -> {
        error("Not correct response")
      }
    }
  }
  suspend fun fileByUrl(
    id: String,
    url: String
  ): Result<Path> = runCatching call@{
    Logger.debug { "Cache by url $url" }
    val path = Res.path(id)
    if (path.exists()) {
      Logger.trace { "File exists,return the path" }
      return@call path
    }
    val tmpPath = Res.tmpPath(id)
    if (tmpPath.exists()) {
      Logger.trace { "TmpFile exists,waiting for the file downloading" }
      suspendCoroutine { res ->
        Res.waitFor(id) { res.resume(it) }
      }
    } else {
      Logger.trace { "TmpFile dont exist" }
      Logger.trace { "Downloading pic" }
      downloadFile(url, tmpPath.toFile(), httpProxy)
      Logger.trace { "Download successfully" }
      put(id, tmpPath)
      path
    }
  }
  fun put(name: String, file: Path): Result<Path> = runCatching {
    Logger.trace { "Put $name" }
    val path = Res.path(name)
    file.moveTo(path)
    Logger.trace { "Move $file to $path" }
    path
  }
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO
}

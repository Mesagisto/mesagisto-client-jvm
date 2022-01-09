@file:Suppress("NOTHING_TO_INLINE", "unused", "MemberVisibilityCanBePrivate")
package org.meowcat.mesagisto.client

import arrow.core.Either
import arrow.core.None
import arrow.core.Option
import arrow.core.Some
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import org.meowcat.mesagisto.client.data.* // ktlint-disable no-wildcard-imports
import java.nio.file.* // ktlint-disable no-wildcard-imports
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.* // ktlint-disable no-wildcard-imports

object Cache : CoroutineScope {

  fun get(name: String): Result<Option<Path>> = runCatching {
    val file = Res.path(name)
    if (file.exists()) Some(file) else None
  }
  suspend fun file(id: ByteArray, url: String?, address: String): Result<Path> {
    return if (url == null) {
      fileById(id, address)
    } else {
      fileByUrl(id, url)
    }
  }
  suspend fun fileById(
    id: ByteArray,
    address: String,
  ): Result<Path> = runCatching call@{
    val idStr = Base64.encodeToString(id)
    Logger.debug { "通过ID${idStr}缓存文件中" }
    val path = Res.path(idStr)
    if (path.exists()) {
      Logger.trace { "文件存在,返回其路径" }
      return@call path
    }
    val tmpPath = Res.tmpPath(idStr)

    if (tmpPath.exists()) return@call suspendCoroutine { res ->
      Logger.trace { "缓存文件存在,正在等待其下载完毕" }
      Res.waitFor(idStr) { res.resume(it) }
    }

    Logger.trace { "缓存文件不存在,正在请求其URL" }
    val packet = Event(EventType.RequestImage(id)).toPacket()
    val response = Server.request(address, packet, Server.LibHeader).getOrThrow()

    return@call when (val rPacket = Packet.fromCbor(response.data).getOrThrow()) {
      is Either.Right -> {
        val event = rPacket.value.data
        if (event !is EventType.RespondImage) error("错误的响应")
        fileByUrl(event.id, event.url).getOrThrow()
      }
      is Either.Left -> {
        error("错误的响应")
      }
    }
  }
  suspend fun fileByUrl(
    id: ByteArray,
    url: String
  ): Result<Path> = runCatching call@{
    val idStr = Base64.encodeToString(id)
    Logger.debug { "通过URL${url}缓存文件." }
    val path = Res.path(idStr)
    if (path.exists()) {
      Logger.trace { "文件存在,返回其路径" }
      return@call path
    }
    val tmpPath = Res.tmpPath(idStr)
    if (tmpPath.exists()) {
      Logger.trace { "缓存文件存在,正在等待其下载完毕" }
      suspendCoroutine { res ->
        Res.waitFor(idStr) { res.resume(it) }
      }
    } else {
      Logger.trace { "缓存文件不存在,尝试下载图片" }
      Net.downloadFile(url, tmpPath)
      Logger.trace { "成功下载图片" }
      put(idStr, tmpPath)
      path
    }
  }
  fun put(name: String, file: Path): Result<Path> = runCatching {
    val path = Res.path(name)
    file.moveTo(path)
    Logger.trace { "将缓存文件 $file 移动至 $path ..." }
    path
  }
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO
}

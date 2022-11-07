@file:Suppress("unused", "MemberVisibilityCanBePrivate", "ktlint:no-wildcard-imports")

package org.mesagisto.client

import kotlinx.coroutines.*
import org.mesagisto.client.data.*
import org.mesagisto.client.utils.Either
import org.mesagisto.client.utils.right
import java.nio.file.*
import java.util.UUID
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.*

object Cache : CoroutineScope {

  fun get(name: String): Result<Path?> = runCatching {
    val file = Res.path(name)
    if (file.exists()) file else null
  }
  suspend fun file(id: ByteArray, url: String?, room: UUID, server: String): Result<Path> {
    return if (url == null) {
      fileById(id, room, server)
    } else {
      fileByUrl(id, url)
    }
  }

  suspend fun fileById(
    id: ByteArray,
    room: UUID,
    server: String
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
    val pkt = Packet.new(room, Event.RequestImage(id).right())
    val resp = Server.request(pkt, server).getOrThrow()

    return@call when (val event = resp.decrypt().getOrThrow()) {
      is Either.Right -> {
        if (event.value !is Event.RespondImage) error("错误的响应")
        fileByUrl(event.value.id, event.value.url).getOrThrow()
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

@file:Suppress("MemberVisibilityCanBePrivate")

package org.mesagisto.client

import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import org.mesagisto.client.data.Event
import org.mesagisto.client.data.Packet
import org.mesagisto.client.utils.Either
import org.mesagisto.client.utils.right
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashSet
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine
import kotlin.io.path.* // ktlint-disable no-wildcard-imports

object Res : CoroutineScope {
  val Directory: Path = Path(System.getProperty("java.io.tmpdir"))
    .resolve("mesagisto")
    .apply { createDirectories() }

  private val handlers = ConcurrentHashMap<String, HashSet<(Path) -> Unit>>()
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default

  init {
    poll()
  }

  private fun poll() = launch {
    while (true) {
      delay(200)
      val forRemove = arrayListOf<String>()
      handlers.forEach {
        if (path(it.key).exists()) {
          forRemove.add(it.key)
        }
      }
      for (remove in forRemove) {
        val handlers = handlers.remove(remove) ?: break
        for (handler in handlers) {
          launch {
            handler.invoke(path(remove))
          }
        }
      }
    }
  }

  fun path(name: String): Path = Directory.resolve(name)
  fun tmpPath(name: String): Path = Directory.resolve("$name.tmp")
  fun waitFor(name: String, handler: (Path) -> Unit) {
    handlers.getOrPut(name) { HashSet() }.add(handler)
  }
  fun storePhotoId(uid: ByteArray, fileId: ByteArray = ByteArray(0)) {
    Db.putImageId(uid, fileId)
  }
  fun get(name: String): Path? {
    val file = path(name)
    return if (file.exists()) file else null
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
      waitFor(idStr) { res.resume(it) }
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
    val path = path(idStr)
    if (path.exists()) {
      Logger.trace { "文件存在,返回其路径" }
      return@call path
    }
    val tmpPath = tmpPath(idStr)
    if (tmpPath.exists()) {
      Logger.trace { "缓存文件存在,正在等待其下载完毕" }
      suspendCoroutine { res ->
        waitFor(idStr) { res.resume(it) }
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
    val path = path(name)
    file.moveTo(path)
    Logger.trace { "将缓存文件 $file 移动至 $path ..." }
    path
  }
}

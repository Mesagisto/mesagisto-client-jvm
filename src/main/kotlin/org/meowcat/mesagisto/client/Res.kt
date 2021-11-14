package org.meowcat.mesagisto.client

import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext
import kotlin.io.path.* // ktlint-disable no-wildcard-imports

object Res : CoroutineScope {
  private var Directory = Path(System.getProperty("java.io.tmpdir"))
    .resolve("mesagisto")
    .apply { createDirectories() }
  private val watcher by lazy {
    FileSystems.getDefault().newWatchService().apply {
      Directory.register(this, StandardWatchEventKinds.ENTRY_CREATE)
    }
  }

  private val handlers = ConcurrentHashMap<String, HashSet<(Path) -> Unit>>()

  private fun poll() {
    val key = watcher.poll() ?: return
    key.pollEvents().mapNotNull {
      when (val context = it.context()) {
        is Path -> context
        else -> null
      }
    }.forEach {
      val handlerSet = handlers[it.name] ?: return@forEach
      handlerSet.forEach { handler ->
        runBlocking {
          launch(Dispatchers.IO) {
            handler(Directory.resolve(it))
          }
        }
      }
      handlerSet.clear()
      handlers.remove(it.name)
    }
    key.reset()
  }
  init {
    launch {
      while (true) {
        delay(100)
        poll()
      }
    }
  }
  fun path(name: String): Path = Directory.resolve(name)
  fun tmpPath(name: String): Path = Directory.resolve("$name.tmp")
  fun waitFor(name: String, handler: (Path) -> Unit) {
    handlers.getOrPut(name) { HashSet() }.add(handler)
  }
  private lateinit var photoUrlResolver: suspend (ByteArray, ByteArray) -> Result<String>
  fun resolvePhotoUrl(
    f: suspend (ByteArray, ByteArray) -> Result<String>
  ) {
    photoUrlResolver = f
  }
  suspend fun getPhotoUrl(uid: ByteArray): String? {
    Logger.trace { "Getting url by id" }
    val fileId = Db.getImageId(uid) ?: return null
    Logger.trace { "Gotten photo id" }
    // fixme
    return photoUrlResolver(uid, fileId).getOrNull()
  }
  suspend fun convertFile(
    id: ByteArray,
    converter: suspend (Path, Path) -> Result<Unit>
  ): Result<Unit> = runCatching {
    val idStr = Base64.encodeToString(id)
    Logger.trace { "converting lib" }
    val path = path(idStr)
    val tmpPath = tmpPath(idStr)
    runInterruptible {
      runCatching {
        val convertPath = path("convert-$idStr")
        tmpPath.createFile()
        path.moveTo(convertPath, true)
        Unit
      }.onFailure {
        it.printStackTrace()
      }
    }.getOrThrow()
    Logger.trace { "invoking converter" }
    converter.invoke(path("convert-$idStr"), tmpPath).onFailure {
      Logger.error(it)
    }
    runInterruptible {
      runCatching {
        tmpPath.moveTo(path, true)
        path("convert-$idStr").deleteIfExists()
        Unit
      }
    }.getOrThrow()
    Logger.trace { "invoke successfully" }
  }
  fun storePhotoId(uid: ByteArray, fileId: ByteArray = ByteArray(0)) {
    Db.putImageId(uid, fileId)
  }
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO
}

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
  private val watcher = FileSystems.getDefault().newWatchService().apply {
    Directory.register(this, StandardWatchEventKinds.ENTRY_CREATE)
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
  private lateinit var photoUrlResolver: suspend (String, String) -> Result<String>
  fun resolvePhotoUrl(
    f: suspend (String, String) -> Result<String>
  ) {
    photoUrlResolver = f
  }
  suspend fun getPhotoUrl(uid: String): String? {
    Logger.trace { "Getting url by id" }
    val fileId = Db.getImageId(uid) ?: return null
    Logger.trace { "Gotten photo id" }
    // fixme
    return photoUrlResolver(uid, fileId).getOrNull()
  }
  suspend fun convertFile(
    name: String,
    converter: suspend (Path, Path) -> Result<Unit>
  ): Result<Unit> = runCatching {
    Logger.trace { "converting lib" }
    val path = path(name)
    val tmpPath = tmpPath(name)
    runInterruptible {
      val convertPath = path("convert-$name")
      convertPath.deleteIfExists()
      tmpPath.createFile()
      path.moveTo(convertPath)
    }
    Logger.trace { "invoking converter" }
    converter.invoke(path("convert-$name"), tmpPath).onFailure {
      Logger.error(it)
    }
    runInterruptible {
      tmpPath.moveTo(path)
      path("convert-$name").deleteIfExists()
    }
    Logger.trace { "invoking successfully" }
  }
  fun storePhotoId(uid: String, fileId: String = "") {
    Db.putImageId(uid, fileId)
  }
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.IO
}

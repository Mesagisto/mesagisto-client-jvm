package org.meowcat.mesagisto.client

import io.nats.client.Dispatcher
import io.nats.client.Message
import io.nats.client.Subscription
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import org.tinylog.kotlin.Logger
import java.io.File
import java.net.Proxy
import java.net.URL
import java.nio.ByteBuffer
import java.nio.file.Path
import java.util.zip.GZIPInputStream
import kotlin.io.path.createDirectories
import kotlin.io.path.deleteIfExists

inline fun Dispatcher.asyncSubscribe(
  subject: String,
  crossinline handler: suspend (Message) -> Unit
): Subscription = subscribe(subject) {
  runBlocking {
    launch(Dispatchers.Default) {
      try {
        handler.invoke(it)
      } catch (e: Throwable) {
        Logger.error(e)
      }
    }
  }
}

suspend fun downloadFile(
  urlStr: String,
  outputFile: File,
  proxy: Proxy? = null
) = runInterruptible {
  val oc = if (proxy == null) {
    URL(urlStr).openConnection()
  } else {
    URL(urlStr).openConnection(proxy)
  }
  val input = if (oc.contentEncoding == "gzip") {
    GZIPInputStream(oc.getInputStream())
  } else {
    oc.getInputStream()
  }
  outputFile.outputStream().buffered().use { output ->
    input.copyTo(output)
  }
}

fun Path.ensureDirectories() {
  runCatching {
    createDirectories()
  }.onFailure {
    deleteIfExists()
    createDirectories()
  }
}

fun Int.toByteArray(): ByteArray =
  ByteBuffer.allocate(4)
    .putInt(this)
    .array()
fun Long.toByteArray(): ByteArray =
  ByteBuffer.allocate(8)
    .putLong(this)
    .array()
fun ByteArray.toI64(): Long? =
  runCatching {
    ByteBuffer.wrap(this)
      .getLong(0)
  }.getOrNull()
fun ByteArray.toI32(): Int? =
  runCatching {
    ByteBuffer.wrap(this)
      .getInt(0)
  }.getOrNull()

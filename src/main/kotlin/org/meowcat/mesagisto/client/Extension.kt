@file:Suppress("BlockingMethodInNonBlockingContext")
package org.meowcat.mesagisto.client

import io.ktor.client.* // ktlint-disable no-wildcard-imports
import io.ktor.client.call.* // ktlint-disable no-wildcard-imports
import io.ktor.client.engine.cio.* // ktlint-disable no-wildcard-imports
import io.ktor.client.request.* // ktlint-disable no-wildcard-imports
import io.ktor.client.statement.* // ktlint-disable no-wildcard-imports
import io.ktor.util.* // ktlint-disable no-wildcard-imports
import io.ktor.utils.io.* // ktlint-disable no-wildcard-imports
import io.ktor.utils.io.core.* // ktlint-disable no-wildcard-imports
import io.ktor.utils.io.streams.* // ktlint-disable no-wildcard-imports
import io.nats.client.Dispatcher
import io.nats.client.Message
import io.nats.client.Subscription
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import java.net.Proxy
import java.nio.ByteBuffer
import java.nio.file.Path
import kotlin.io.path.outputStream

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
private val HTTP_CLIENT = HttpClient(CIO)
@OptIn(KtorExperimentalAPI::class)
suspend fun downloadFile(
  urlStr: String,
  outputFile: Path,
  proxy: Proxy? = null
) {
  // fixme proxy
//  client.config {
//    engine {
//      this.proxy = proxy
//    }
//  }
  HTTP_CLIENT.download(urlStr, outputFile)
}
// from https://www.cnblogs.com/soclear/p/15167898.html
suspend fun HttpClient.download(
  url: String,
  file: Path
) = withContext(Dispatchers.IO) fn@{
  this@download.get<HttpStatement>(url).execute { res ->
    val channel: ByteReadChannel = res.receive()
    val output = file.outputStream()
    while (!channel.isClosedForRead) {
      val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
      if (!packet.isEmpty) {
        output.writePacket(packet)
      }
    }
    output.close()
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

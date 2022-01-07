package org.meowcat.mesagisto.client

import io.nats.client.Dispatcher
import io.nats.client.Message
import io.nats.client.Subscription
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import java.nio.ByteBuffer

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

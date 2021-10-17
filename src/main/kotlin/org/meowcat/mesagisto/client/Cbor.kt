package org.meowcat.mesagisto.client

import kotlinx.serialization.* // ktlint-disable no-wildcard-imports
import kotlinx.serialization.cbor.Cbor

@OptIn(ExperimentalSerializationApi::class)
object Cbor {

  @OptIn(InternalSerializationApi::class)
  val inner = Cbor {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  inline fun <reified T> encodeToByteArray(value: T): ByteArray =
    inner.encodeToByteArray(value)

  inline fun <reified T> decodeFromByteArray(bytes: ByteArray): T =
    inner.decodeFromByteArray(bytes)
}

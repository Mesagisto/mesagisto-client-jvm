package org.meowcat.mesagisto.client

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.cbor.Cbor
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.encodeToByteArray

@OptIn(ExperimentalSerializationApi::class)
object Cbor {

  val inner = Cbor {
    encodeDefaults = true
    ignoreUnknownKeys = true
  }

  inline fun <reified T> encodeToByteArray(value: T): ByteArray =
    inner.encodeToByteArray(value)

  inline fun <reified T> decodeFromByteArray(bytes: ByteArray): T =
    inner.decodeFromByteArray(bytes)
}

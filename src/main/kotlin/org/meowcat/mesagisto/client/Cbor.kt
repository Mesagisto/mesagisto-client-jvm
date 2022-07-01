package org.meowcat.mesagisto.client

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.cbor.databind.CBORMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule

object Cbor {
  var mapper: ObjectMapper = CBORMapper().registerKotlinModule().apply {
    configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  }

  inline fun <reified T> encodeToByteArray(value: T): ByteArray =
    mapper.writeValueAsBytes(value)

  inline fun <reified T> decodeFromByteArray(bytes: ByteArray): T =
    mapper.readValue(bytes, T::class.java)
}

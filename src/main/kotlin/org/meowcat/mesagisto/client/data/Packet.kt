@file:Suppress("ArrayInDataClass")
package org.meowcat.mesagisto.client.data

import org.meowcat.mesagisto.client.Cbor
import org.meowcat.mesagisto.client.Cipher

data class Packet(
  val type: String = "",
  val content: ByteArray = ByteArray(0),
  val encrypt: ByteArray = ByteArray(0),
  val version: String = "v1"
) {
  fun toCbor(): ByteArray = Cbor.encodeToByteArray(this)

  companion object {
    fun from(data: Either<Message, Event>): Packet {
      val nonce = Cipher.newNonce()
      val ty: String
      val bytes = when (data) {
        is Either.Left -> {
          ty = "message"
          val bytes = Cbor.encodeToByteArray(data.value)
          Cipher.encrypt(bytes, nonce)
        }
        is Either.Right -> {
          ty = "event"
          val bytes = Cbor.encodeToByteArray(data.value)
          Cipher.encrypt(bytes, nonce)
        }
      }
      return Packet(
        ty,
        content = bytes,
        encrypt = nonce,
        "v1"
      )
    }
    fun fromCbor(
      data: ByteArray
    ): Result<Either<Message, Event>> = runCatching {
      val packet: Packet = Cbor.decodeFromByteArray(data)
      val plaintext = Cipher.decrypt(packet.content, packet.encrypt)
      when (packet.type) {
        "message" -> Cbor.decodeFromByteArray<Message>(plaintext).left()
        "event" -> Cbor.decodeFromByteArray<Event>(plaintext).right()
        else -> throw IllegalStateException("Unreachable code")
      }
    }
  }
}


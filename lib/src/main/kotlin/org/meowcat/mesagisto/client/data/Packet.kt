package org.meowcat.mesagisto.client.data

import arrow.core.Either
import kotlinx.serialization.* // ktlint-disable no-wildcard-imports
import kotlinx.serialization.cbor.ByteString
import org.meowcat.mesagisto.client.Cbor
import org.meowcat.mesagisto.client.Cipher

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class Packet(
  val type: String,
  @ByteString
  val content: ByteArray,
  @ByteString
  val encrypt: ByteArray? = null,
  val version: String
) {
  fun toCbor(): ByteArray {
    return Cbor.encodeToByteArray(this)
  }

  companion object Factory {
    fun from(data: Either<Message, Event>): Packet {
      val ty: String
      val bytes = when (data) {
        is Either.Left -> {
          ty = "message"
          Cbor.encodeToByteArray(data.value)
        }
        is Either.Right -> {
          ty = "event"
          Cbor.encodeToByteArray(data.value)
        }
      }
      return Packet(
        ty,
        content = bytes,
        encrypt = null,
        "v1"
      )
    }
    fun encryptFrom(data: Either<Message, Event>): Packet {
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
      if (packet.type == "message") {
        if (packet.encrypt == null) {
          val message = Cbor.decodeFromByteArray<Message>(packet.content)
          Either.Left(message)
        } else {
          handleEncrypt(packet, true).getOrThrow()
        }
      } else if (packet.type == "event") {
        if (packet.encrypt == null) {
          val event: Event = Cbor.decodeFromByteArray(packet.content)
          Either.Right(event)
        } else {
          handleEncrypt(packet, false).getOrThrow()
        }
      } else {
        throw IllegalStateException("Unreachable code")
      }
    }
    private fun handleEncrypt(
      packet: Packet,
      ty: Boolean
    ): Result<Either<Message, Event>> = runCatching run@{
      val nonce = packet.encrypt!!

      val plaintext = Cipher.decrypt(packet.content, nonce)
      return@run if (ty) {
        Either.Left(Cbor.decodeFromByteArray(plaintext))
      } else {
        Either.Right(Cbor.decodeFromByteArray(plaintext))
      }
    }
  }
}

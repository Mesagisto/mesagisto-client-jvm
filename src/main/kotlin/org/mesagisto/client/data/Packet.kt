@file:Suppress("NOTHING_TO_INLINE", "ArrayInDataClass", "MemberVisibilityCanBePrivate")

package org.mesagisto.client.data

import com.fasterxml.jackson.annotation.* // ktlint-disable no-wildcard-imports
import org.mesagisto.client.Cbor
import org.mesagisto.client.Cipher
import org.mesagisto.client.utils.Either
import org.mesagisto.client.utils.left
import org.mesagisto.client.utils.right
import java.util.UUID

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "t")
@JsonSubTypes(
  JsonSubTypes.Type(Ctl.Sub::class, name = "sub"),
  JsonSubTypes.Type(Ctl.Unsub::class, name = "unsub")
)
sealed class Ctl {

  class Sub : Ctl() {
    override fun equals(other: Any?): Boolean {
      return this === other
    }

    override fun hashCode(): Int {
      return System.identityHashCode(this)
    }
  }

  class Unsub : Ctl() {
    override fun equals(other: Any?): Boolean {
      return this === other
    }

    override fun hashCode(): Int {
      return System.identityHashCode(this)
    }
  }

  companion object {
    val SUB = Sub()
    val UNSUB = Unsub()
  }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "t")
@JsonSubTypes(
  JsonSubTypes.Type(Inbox.Request::class, name = "req"),
  JsonSubTypes.Type(Inbox.Respond::class, name = "res")
)
sealed class Inbox {
  data class Request(val id: UUID = UUID.randomUUID()) : Inbox()
  data class Respond(val id: UUID = UUID.randomUUID()) : Inbox()
}

data class Packet constructor(
  val t: String = "",
  val c: ByteArray = ByteArray(0),
  val n: ByteArray = ByteArray(0),
  val rid: UUID = UUID.randomUUID(),
  var inbox: Inbox? = null,
  val ctl: Ctl? = null
) {
  fun toCbor(): ByteArray = Cbor.encodeToByteArray(this)

  fun decrypt(): Result<Either<Message, Event>> = runCatching {
    val plain = Cipher.decrypt(content, nonce)
    when (type) {
      "message" -> Cbor.decodeFromByteArray<Message>(plain).left()
      "event" -> Cbor.decodeFromByteArray<Event>(plain).right()
      else -> throw IllegalStateException("Unreachable code")
    }
  }
  companion object {
    fun new(roomId: UUID, data: Either<Message, Event>): Packet {
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
        c = bytes,
        n = nonce,
        rid = roomId
      )
    }
    fun newSub(roomId: UUID): Packet = Packet(
      rid = roomId,
      t = "ctl",
      ctl = Ctl.SUB
    )
    fun newUnsub(roomId: UUID): Packet = Packet(
      rid = roomId,
      t = "ctl",
      ctl = Ctl.UNSUB
    )
    inline fun fromCbor(
      data: ByteArray
    ): Result<Packet> = runCatching {
      val packet: Packet = Cbor.decodeFromByteArray(data)
      packet
    }
  }
}

val Packet.type
  get() = t
val Packet.content
  get() = c
val Packet.nonce
  get() = n
val Packet.roomId
  get() = rid

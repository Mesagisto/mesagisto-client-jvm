@file:Suppress("ArrayInDataClass", "unused")
package org.meowcat.mesagisto.client.data

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import org.jetbrains.annotations.TestOnly
import org.meowcat.mesagisto.client.Cbor
import org.meowcat.mesagisto.client.toHex

fun Message.toPacket(): Packet = Packet.from(this.left())

data class Message(
  val profile: Profile,

  val id: ByteArray,
  val reply: ByteArray? = null,
  val chain: List<MessageType>
)

data class Profile(
  val id: ByteArray,
  val username: String? = null,
  val nick: String? = null,
)

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed class MessageType {
  @JsonTypeName("text")
  data class Text(
    val content: String
  ) : MessageType()
  @JsonTypeName("image")
  data class Image(
    // unique id supplied by platform
    val id: ByteArray,
    val url: String? = null,
  ) : MessageType()
}

@TestOnly
fun main() {
  val _msg_1 = MessageType.Text("dd")
  val msg = Message(
    profile = Profile("dd".toByteArray()),
    id = "id".toByteArray(),
    chain = listOf(_msg_1)
  )

  val bytes = Cbor.encodeToByteArray(msg)
  println(bytes.toHex())
  val _msgDe: Message = Cbor.decodeFromByteArray(bytes)
}

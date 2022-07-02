@file:Suppress("ArrayInDataClass", "unused")
package org.meowcat.mesagisto.client.data

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

fun Message.toPacket(): Packet = Packet.from(this.left())

data class Message(
  val profile: Profile = Profile(),
  val id: ByteArray = ByteArray(0),
  val reply: ByteArray? = null,
  val chain: List<MessageType> = emptyList()
)

data class Profile(
  val id: ByteArray = ByteArray(0),
  val username: String? = null,
  val nick: String? = null,
)
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(
  Type(MessageType.Text::class, name = "text"),
  Type(MessageType.Image::class, name = "image")
)
sealed class MessageType {
  @JsonTypeName("text")
  data class Text(
    val content: String = ""
  ) : MessageType()
  @JsonTypeName("image")
  data class Image(
    // unique id supplied by platform
    val id: ByteArray = ByteArray(0),
    val url: String? = null,
  ) : MessageType()
}

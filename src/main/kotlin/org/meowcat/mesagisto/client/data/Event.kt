@file:Suppress("ArrayInDataClass", "unused")
package org.meowcat.mesagisto.client.data

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName

fun Event.toPacket(): Packet = Packet.from(this.right())

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed class Event {
  @JsonTypeName("request_image")
  data class RequestImage(
    val id: ByteArray,
  ) : Event()

  @JsonTypeName("respond_image")
  data class RespondImage(
    val id: ByteArray,
    val url: String
  ) : Event()
}

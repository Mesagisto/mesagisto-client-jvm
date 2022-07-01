@file:Suppress("ArrayInDataClass", "unused")
package org.meowcat.mesagisto.client.data

import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import org.jetbrains.annotations.TestOnly
import org.meowcat.mesagisto.client.Cbor
import org.meowcat.mesagisto.client.toHex

fun Event.toPacket(): Packet = Packet.from(this.right())
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
sealed class Event {
  @JsonTypeName("request_image")
  class RequestImage(
    val id: ByteArray,
  ) : Event()

  @JsonTypeName("respond_image")
  class RespondImage(
    val id: ByteArray,
    val url: String
  ) : Event()
}

@file:Suppress("ArrayInDataClass", "unused")
package org.meowcat.mesagisto.client.data

import arrow.core.right
import kotlinx.serialization.* // ktlint-disable no-wildcard-imports
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.element
import kotlinx.serialization.encoding.* // ktlint-disable no-wildcard-imports

@Serializable
data class Event(
  @Serializable(with = FixedEventSerializer::class)
  val data: EventType
)

@Serializable
sealed class EventType {
  @Serializable
  @SerialName("request_image")
  data class RequestImage(
    @ByteString
    val id: ByteArray,
  ) : EventType()

  @Serializable
  @SerialName("respond_image")
  data class RespondImage(
    @ByteString
    val id: ByteArray,
    val url: String
  ) : EventType()
  fun toEvent(): Event = Event(this)
}

object FixedEventSerializer : KSerializer<EventType> {
  override var descriptor: SerialDescriptor = buildClassSerialDescriptor(
    EventType.serializer().descriptor.serialName
  ) {
    element<String>("t")
    element<String>("c")
  }
  override fun serialize(encoder: Encoder, value: EventType) = when (value) {
    is EventType.RequestImage -> {
      encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, EventType.RequestImage.serializer().descriptor.serialName)
        encodeSerializableElement(descriptor, 1, EventType.RequestImage.serializer(), value)
      }
    }
    is EventType.RespondImage -> {
      encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, EventType.RespondImage.serializer().descriptor.serialName)
        encodeSerializableElement(descriptor, 1, EventType.RespondImage.serializer(), value)
      }
    }
  }
  override fun deserialize(decoder: Decoder): EventType = decoder.decodeStructure(descriptor) {
    var t = ""
    var res: EventType? = null
    while (true) {
      when (val index = decodeElementIndex(descriptor)) {
        0 -> t = decodeStringElement(descriptor, 0)
        1 -> res = when (t) {
          EventType.RequestImage.serializer().descriptor.serialName -> decodeSerializableElement(
            descriptor, 1, EventType.RequestImage.serializer()
          )
          EventType.RespondImage.serializer().descriptor.serialName -> decodeSerializableElement(
            descriptor, 1, EventType.RespondImage.serializer()
          )
          else -> throw IllegalStateException("Unexpected type $t")
        }
        CompositeDecoder.DECODE_DONE -> break
        else -> error("Unexpected index: $index")
      }
    }
    return@decodeStructure res!!
  }
}

fun Event.toPacket(): Packet = Packet.from(this.right())

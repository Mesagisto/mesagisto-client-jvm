package org.meowcat.mesagisto.client.data

import arrow.core.left
import kotlinx.serialization.* // ktlint-disable no-wildcard-imports
import kotlinx.serialization.cbor.ByteString
import kotlinx.serialization.descriptors.* // ktlint-disable no-wildcard-imports
import kotlinx.serialization.encoding.* // ktlint-disable no-wildcard-imports
import kotlinx.serialization.encoding.Encoder

fun Message.toPacket(): Packet = Packet.from(this.left())
fun Message.toCipherPacket(): Packet = Packet.encryptFrom(this.left())

@Serializable
data class Message @OptIn(ExperimentalSerializationApi::class) constructor(
  val profile: Profile,
  @ByteString
  val id: ByteArray,
  @ByteString
  val reply: ByteArray? = null,
  val chain: List<@Serializable(with = FixedMessageSerializer::class) MessageType>
)

@Serializable
data class Profile(
  val id: Long,
  val username: String? = null,
  val nick: String? = null,
)

@Serializable
sealed class MessageType {
  @Serializable
  @SerialName("text")
  class Text(
    val content: String
  ) : MessageType()

  @Serializable
  @SerialName("image")
  class Image(
    // unique id supplied by platform
    val id: String,
    val url: String? = null,
  ) : MessageType()
}

object FixedMessageSerializer : KSerializer<MessageType> {

  @OptIn(ExperimentalSerializationApi::class)
  override var descriptor: SerialDescriptor = buildClassSerialDescriptor(
    MessageType.serializer().descriptor.serialName
  ) {
    element<String>("t")
    element<String>("c")
  }

  @OptIn(InternalSerializationApi::class, ExperimentalSerializationApi::class)
  override fun serialize(encoder: Encoder, value: MessageType) = when (value) {
    is MessageType.Text -> {
      encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, MessageType.Text.serializer().descriptor.serialName)
        encodeSerializableElement(descriptor, 1, MessageType.Text.serializer(), value)
      }
    }
    is MessageType.Image -> {
      encoder.encodeStructure(descriptor) {
        encodeStringElement(descriptor, 0, MessageType.Image.serializer().descriptor.serialName)
        encodeSerializableElement(descriptor, 1, MessageType.Image.serializer(), value)
      }
    }
  }

  @OptIn(ExperimentalSerializationApi::class)
  override fun deserialize(decoder: Decoder): MessageType = decoder.decodeStructure(descriptor) {
    var t = ""
    var res: MessageType? = null
    while (true) {
      when (val index = decodeElementIndex(descriptor)) {
        0 -> t = decodeStringElement(descriptor, 0)
        1 -> res = when (t) {
          MessageType.Text.serializer().descriptor.serialName -> decodeSerializableElement(
            descriptor, 1, MessageType.Text.serializer()
          )
          MessageType.Image.serializer().descriptor.serialName -> decodeSerializableElement(
            descriptor, 1, MessageType.Image.serializer()
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
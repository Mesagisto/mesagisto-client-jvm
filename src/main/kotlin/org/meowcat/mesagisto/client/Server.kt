package org.meowcat.mesagisto.client

import arrow.core.Either
import arrow.core.right
import io.nats.client.Connection
import io.nats.client.Message
import io.nats.client.Nats
import io.nats.client.Subscription
import io.nats.client.impl.Headers
import io.nats.client.impl.NatsMessage
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.future.await
import org.meowcat.mesagisto.client.data.EventType
import org.meowcat.mesagisto.client.data.Packet
import org.tinylog.kotlin.Logger
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

object Server : CoroutineScope {
  private lateinit var Address: String
  private val NC: Connection by lazy {
    Logger.info { "Trying to connect to nats server: $Address" }
    val nc = runCatching {
      Nats.connect(Address)
    }.recoverCatching {
      when (it) {
        is IOException -> {
          Logger.info { "Failed to connection,auto retrying..." }
          Nats.connect(Address)
        }
        else -> throw it
      }
    }.getOrThrow()

    Logger.info { "Connect nats successfully" }
    return@lazy nc
  }
  private val CID by lazy { NC.serverInfo.clientId.toString() }
  internal val NatsHeader by lazy {
    Headers().add("meta", "cid=$CID")
  }
  internal val LibHeader by lazy {
    Headers().add("meta", "cid=$CID", "lib")
  }
  private val Dispatcher by lazy { NC.createDispatcher { } }
  private val Endpoint by lazy { ConcurrentHashMap<Long, Subscription>() }

  private val CompatAddress by lazy { ConcurrentHashMap<String, String>() }

  fun initNC(address: String) {
    Address = address
    NC
    CID
    NatsHeader
  }

  fun compatAddress(address: String): String =
    CompatAddress.getOrPut(address) {
      val digest = MessageDigest.getInstance("SHA-256")
      val compatAddress = digest.digest("$address${Cipher.RAW_KEY}".toByteArray())
      "compat.${Base64.encodeToString(compatAddress)}"
    }

  suspend fun sendAndRegisterReceive(
    target: Long,
    address: String,
    content: Packet,
    headers: Headers? = null,
    handler: suspend (Message, Long) -> Result<Unit>
  ): Unit = withContext(Dispatchers.Default) run@{
    withContext(Dispatchers.IO) {
      NC.publish(
        NatsMessage.builder()
          .data(Cbor.encodeToByteArray(content))
          .subject(compatAddress(address))
          .headers(headers ?: NatsHeader)
          .build()
      )
    }
    Logger.trace { "Trying to create sub for $target" }
    Endpoint.getOrPut(target) {
      val compatAddress = compatAddress(address)
      Logger.trace { "Creating sub on $compatAddress for $target with compatibility" }
      Dispatcher.asyncSubscribe(compatAddress) sub@{ msg ->
        if (msg.headers["meta"].contains("cid=$CID")) return@sub
        if (msg.headers["meta"].contains("lib")) {
          Logger.debug { "Handling message sent by lib" }
          when (val packet = Packet.fromCbor(msg.data).getOrThrow()) {
            is Either.Right -> {
              when (val kind = packet.value.data) {
                is EventType.RequestImage -> {
                  Logger.trace { "Received request image event" }
                  val url = Res.getPhotoUrl(kind.id) ?: run {
                    Logger.trace { "Cannot get image url" }
                    return@sub
                  }
                  Logger.trace { "got image url" }
                  val event = EventType.RespondImage(kind.id, url).toEvent()
                  Logger.trace { "Creating response" }
                  val cborBytes = Packet.from(event.right()).toCbor()
                  withContext(Dispatchers.IO) {
                    NC.publish(
                      NatsMessage.builder()
                        .data(cborBytes)
                        .subject(msg.replyTo)
                        .build()
                    )
                  }
                }
                else -> return@sub
              }
            }
            else -> return@sub
          }
          return@sub
        }
        Logger.trace { "Received message of target $target" }
        handler(msg, target).onFailure { err ->
          Logger.error(err)
        }
      }
    }
  }

  suspend fun request(
    address: String,
    content: Packet,
    headers: Headers? = null
  ): Result<Message> = runCatching {
    withContext(Dispatchers.IO) {
      val message = NatsMessage(
        compatAddress(address), null, headers ?: NatsHeader,
        Cbor.encodeToByteArray(content)
      )
      withTimeout(15_000L) {
        NC.request(message).await()
      }
    }
  }
  override val coroutineContext: CoroutineContext
    get() = Dispatchers.Default
}

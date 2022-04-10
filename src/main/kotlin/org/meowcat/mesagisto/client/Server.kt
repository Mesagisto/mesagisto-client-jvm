package org.meowcat.mesagisto.client

import io.nats.client.Connection
import io.nats.client.Message
import io.nats.client.Nats
import io.nats.client.Subscription
import io.nats.client.impl.Headers
import io.nats.client.impl.NatsMessage
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.future.await
import org.meowcat.mesagisto.client.data.Either
import org.meowcat.mesagisto.client.data.EventType
import org.meowcat.mesagisto.client.data.Packet
import org.meowcat.mesagisto.client.data.right
import java.io.Closeable
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

object Server : CoroutineScope,Closeable {
  private lateinit var Address: String
  private val NC: Connection by lazy {
    Logger.info { ("正在尝试连接到NATS服务器: $Address") }
    val nc = runCatching {
      Nats.connect(Address)
    }.recoverCatching {
      when (it) {
        is IOException -> {
          Logger.info { "连接失败,正在重生..." }
          Nats.connect(Address)
        }
        else -> throw it
      }
    }.getOrThrow()

    Logger.info { "成功连接到NATS服务器" }
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
      val sha256Address = digest.digest(Cipher.uniqueAddress(address).toByteArray())
      "compat.${Base64.encodeToString(sha256Address)}"
    }

  override fun close() {
    NC.closeDispatcher(Dispatcher)
    NC.close()
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
    Endpoint.getOrPut(target) {
      val compatAddress = compatAddress(address)
      Logger.trace { "为目标${target}创建向下兼容订阅中,兼容订阅地址为:$compatAddress " }
      Dispatcher.asyncSubscribe(compatAddress) sub@{ msg ->
        if (msg.headers["meta"].contains("cid=$CID")) return@sub
        if (msg.headers["meta"].contains("lib")) {
          Logger.debug { "正在处理由程序库发送的数据..." }
          when (val packet = Packet.fromCbor(msg.data).getOrThrow()) {
            is Either.Right -> {
              when (val kind = packet.value.data) {
                is EventType.RequestImage -> {
                  Logger.trace { "接收到图片URL请求事件" }
                  val url = Res.getPhotoUrl(kind.id) ?: run {
                    Logger.trace { "无法从本地获取该图片URL, 忽略该事件" }
                    return@sub
                  }
                  val event = EventType.RespondImage(kind.id, url).toEvent()
                  Logger.trace { "得到图片URL,正在响应..." }
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
        Logger.trace { "接收到目标 $target 的消息" }
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

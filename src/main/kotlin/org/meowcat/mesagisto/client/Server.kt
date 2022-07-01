package org.meowcat.mesagisto.client

import io.nats.client.Connection
import io.nats.client.Message
import io.nats.client.Nats
import io.nats.client.Subscription
import io.nats.client.impl.Headers
import io.nats.client.impl.NatsMessage
import kotlinx.coroutines.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.future.await
import org.meowcat.mesagisto.client.data.*
import java.io.Closeable
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.CoroutineContext

object Server : CoroutineScope, Closeable {
  private lateinit var NC: Connection

  private val NatsHeader: ThreadLocal<Headers> = ThreadLocal.withInitial { Headers() }
  internal val LibHeader by lazy { Headers().add("meta", "lib", "cid=$CID") }
  private val Dispatcher by lazy { NC.createDispatcher { } }
  private val Endpoint = ConcurrentHashMap<String, Subscription>()
  private val uniqueAddress = ConcurrentHashMap<String, String>()

  private val CID by lazy { NC.serverInfo.clientId }

  fun initNC(address: String) {
    NC = run {
      Logger.info { "正在尝试连接到NATS服务器: $address" }
      val nc = runCatching {
        Nats.connect(address)
      }.recoverCatching {
        when (it) {
          is IOException -> {
            Logger.info { "连接失败,正在重试..." }
            Nats.connect(address)
          }
          else -> throw it
        }
      }.getOrThrow()

      Logger.info { "成功连接到NATS服务器" }
      nc
    }
  }

  fun uniqueAddress(address: String): String = uniqueAddress.getOrPut(address) {
    val digest = MessageDigest.getInstance("SHA-256")
    val uniqueAddress = Cipher.uniqueAddress(address)
    val sha256Address = digest.digest(uniqueAddress.toByteArray())
    Base64.encodeToString(sha256Address)
  }

  override fun close() {
    NC.closeDispatcher(Dispatcher)
    NC.close()
  }

  suspend fun send(
    target: String,
    address: String,
    content: Packet,
    headers: Headers? = null,
  ) {
    withContext(Dispatchers.IO) {
      NC.publish(
        NatsMessage.builder()
          .data(Cbor.encodeToByteArray(content))
          .subject(uniqueAddress(address))
          .headers(
            headers ?: NatsHeader.get().apply {
              remove("meta")
              add("meta", "sender=$target")
            }
          )
          .build()
      )
    }
  }
  suspend fun recv(
    target: String,
    address: String,
    handler: suspend (Message, String) -> Result<Unit>
  ) {
    Endpoint.getOrPut(target) {
      val uniqueAddress = uniqueAddress(address)
      Logger.trace { "为目标${target}创建订阅中,地址为:$uniqueAddress " }
      Dispatcher.asyncSubscribe(uniqueAddress) subscribe@{ msg ->
        if (!msg.headers.isNotSelf(target)) return@subscribe
        if (msg.headers.isRemoteLib(CID)) {
          Logger.debug { "正在处理由程序库发送的数据..." }
          when (val packet = Packet.fromCbor(msg.data).getOrThrow()) {
            is Either.Right -> {
              when (val kind = packet.value) {
                is Event.RequestImage -> {
                  Logger.trace { "接收到图片URL请求事件" }
                  val url = Res.getPhotoUrl(kind.id) ?: run {
                    Logger.trace { "无法从本地获取该图片URL, 忽略该事件" }
                    return@subscribe
                  }
                  val event = Event.RespondImage(kind.id, url)
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
                else -> return@subscribe
              }
            }
            else -> return@subscribe
          }
          return@subscribe
        }
        Logger.trace { "接收到目标 $target 的消息" }
        handler(msg, target).onFailure { err ->
          Logger.error(err)
        }
      }
    }
  }

  fun unsub(
    target: String,
  ) {
    val ep = Endpoint.remove(target) ?: return
    Dispatcher.unsubscribe(ep)
  }

  suspend fun request(
    address: String,
    content: Packet,
    headers: Headers? = null
  ): Result<Message> = runCatching {
    withContext(Dispatchers.IO) {
      val message = NatsMessage(
        uniqueAddress(address), null, headers ?: NatsHeader.get(),
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

fun Headers.isNotSelf(target: String): Boolean =
  !this["meta"].contains("sender=$target")

fun Headers.isRemoteLib(cid: Int): Boolean =
  this["meta"].contains("lib") && !this["meta"].contains("cid=$cid")

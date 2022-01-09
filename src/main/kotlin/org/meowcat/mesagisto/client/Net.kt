@file:Suppress("BlockingMethodInNonBlockingContext")
package org.meowcat.mesagisto.client

import io.ktor.client.* // ktlint-disable no-wildcard-imports
import io.ktor.client.call.* // ktlint-disable no-wildcard-imports
import io.ktor.client.engine.cio.* // ktlint-disable no-wildcard-imports
import io.ktor.client.request.* // ktlint-disable no-wildcard-imports
import io.ktor.client.statement.* // ktlint-disable no-wildcard-imports
import io.ktor.util.* // ktlint-disable no-wildcard-imports
import io.ktor.utils.io.* // ktlint-disable no-wildcard-imports
import io.ktor.utils.io.core.* // ktlint-disable no-wildcard-imports
import io.ktor.utils.io.streams.* // ktlint-disable no-wildcard-imports
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.nio.file.Path
import kotlin.io.path.outputStream

object Net {
  private var HTTP_CLIENT = HttpClient(CIO)

  @OptIn(KtorExperimentalAPI::class)
  fun setProxy(proxyUri: String) {
    Logger.info { "设置代理为 $proxyUri" }
    val proxy = run {
      val uri = URI(proxyUri)
      val type = when (uri.scheme) {
        "http" -> Proxy.Type.HTTP
        "socks" -> Proxy.Type.SOCKS
        else -> Proxy.Type.DIRECT
      }
      Proxy(type, InetSocketAddress(uri.host, uri.port))
    }
    HTTP_CLIENT = HttpClient(CIO) {
      engine {
        this.proxy = proxy
      }
    }
  }
  @OptIn(KtorExperimentalAPI::class)
  suspend fun downloadFile(
    urlStr: String,
    outputFile: Path,
  ) {
    HTTP_CLIENT.download(urlStr, outputFile)
  }
  // from https://www.cnblogs.com/soclear/p/15167898.html
  private suspend fun HttpClient.download(
    url: String,
    file: Path
  ) = withContext(Dispatchers.IO) fn@{
    this@download.get<HttpStatement>(url).execute { res ->
      val channel: ByteReadChannel = res.receive()
      val output = file.outputStream()
      while (!channel.isClosedForRead) {
        val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
        if (!packet.isEmpty) {
          output.writePacket(packet)
        }
      }
      output.close()
    }
  }
}

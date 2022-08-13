@file:Suppress("MemberVisibilityCanBePrivate")

package org.mesagisto.client

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mesagisto.client.utils.ControlFlow

class MesagistoConfig {
  var name: String = "default"
  var cipherKey: String = ""
  var remotes: HashMap<String, String> = HashMap()
  var packetHandler: PackHandler? = null
  var proxyEnable = false
  var proxyUri = "http://127.0.0.1:7890"

  suspend fun apply() {
    Cipher.init(cipherKey)
    Db.init(name)
    if (proxyEnable) {
      Net.setProxy(proxyUri)
    }
    Server.packetHandler = packetHandler!!
    Server.init(remotes)
  }

  companion object {
    fun builder(
      build: MesagistoConfig.() -> Unit
    ): MesagistoConfig {
      val builder = MesagistoConfig()
      build(builder)
      return builder
    }
  }
}
suspend fun main() {
  val config = MesagistoConfig.builder {
    name = "test"
    cipherKey = "test"
    proxyEnable = false
    remotes = HashMap<String, String>().apply {
      put("mesagisto", "ws://center.itsusinn.site:6996")
    }
    packetHandler = {
      println("${it.decrypt()}")
      Result.success(ControlFlow.Continue(Unit))
    }
  }

  withContext(Dispatchers.Default) {
    config.apply()
  }
  println("i am ok!")
}

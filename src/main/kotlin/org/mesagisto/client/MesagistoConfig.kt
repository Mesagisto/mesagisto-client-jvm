@file:Suppress("MemberVisibilityCanBePrivate")

package org.mesagisto.client

class MesagistoConfig {
  var name: String = "default"
  var cipherKey: String = ""
  var remotes: MutableMap<String, String> = HashMap()
  var packetHandler: PackHandler? = null
  var proxyEnable = false
  var proxyUri = "http://127.0.0.1:7890"
  var sameSideDeliver = true
  suspend fun apply() {
    Cipher.init(cipherKey)
    Db.init(name)
    if (proxyEnable) {
      Net.setProxy(proxyUri)
    }
    Server.packetHandler = packetHandler!!
    Server.init(remotes, sameSideDeliver)
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

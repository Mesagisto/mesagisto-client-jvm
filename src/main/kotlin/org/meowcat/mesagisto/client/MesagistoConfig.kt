package org.meowcat.mesagisto.client

class MesagistoConfig {
  var name: String = "default"
  var natsAddress = "nats://itsusinn.site:4222"
  var cipherKey: String = ""
  var resolvePhotoUrl: (suspend (ByteArray, ByteArray) -> Result<String>)? = null
  var proxyEnable = false
  var proxyUri = "http://127.0.0.1:7890"

  fun apply() {
    Cipher.init(cipherKey)
    Db.init(name)
    Server.initNC(natsAddress)
    if (resolvePhotoUrl == null) {
      Res.resolvePhotoUrl { _, _ ->
        Result.failure(IllegalStateException("Unreachable"))
      }
    } else {
      Res.resolvePhotoUrl(resolvePhotoUrl!!)
    }
    if (proxyEnable) {
      Net.setProxy(proxyUri)
    }
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

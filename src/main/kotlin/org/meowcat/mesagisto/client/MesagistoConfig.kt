package org.meowcat.mesagisto.client

class MesagistoConfig {
  var name: String = "default"
  var natsAddress = "nats://itsusinn.site:4222"
  var cipherEnable: Boolean = true
  var cipherKey: String = ""
  var cipherRefusePlain: Boolean = true
  var resolvePhotoUrl: (suspend (ByteArray, ByteArray) -> Result<String>)? = null

  fun apply() {
    if (cipherEnable) {
      Cipher.init(cipherKey, cipherRefusePlain)
    } else {
      Cipher.deinit()
    }
    Db.init(name)
    Server.initNC(natsAddress)
    if (resolvePhotoUrl == null) {
      Res.resolvePhotoUrl { _, _ ->
        Result.failure(IllegalStateException("Unreachable"))
      }
    } else {
      Res.resolvePhotoUrl(resolvePhotoUrl!!)
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

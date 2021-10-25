package org.meowcat.mesagisto.client

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.getOrSet
import kotlin.properties.Delegates

object Cipher {
  private lateinit var KEY: KeyParameter
  internal lateinit var RAW_KEY: String
  private val SECURE_RANDOM: SecureRandom by lazy { SecureRandom() }
  var ENABLE by Delegates.notNull<Boolean>()
    private set
  var REFUSE_PLAIN by Delegates.notNull<Boolean>()
    private set
  private val inner: ThreadLocal<GCMBlockCipher> = ThreadLocal()
  fun newNonce(): ByteArray {
    val nonce = ByteArray(12)
    SECURE_RANDOM.nextBytes(nonce)
    return nonce
  }
  fun init(key: String, refusePlain: Boolean = true) {
    ENABLE = true
    REFUSE_PLAIN = refusePlain
    RAW_KEY = key
    val digest = MessageDigest.getInstance("SHA-256")
    val key256 = digest.digest(key.toByteArray())
    KEY = KeyParameter(key256)
  }
  fun deinit() {
    ENABLE = false
  }
  fun encrypt(plaintext: ByteArray, nonce: ByteArray): ByteArray {
    val cipher = inner.getOrSet { GCMBlockCipher(AESEngine()) }
    val key = AEADParameters(KEY, 128, nonce)
    val encryptedBytes = with(cipher) {
      init(true, key)
      val res = ByteArray(getOutputSize(plaintext.size))
      val retLen = processBytes(plaintext, 0, plaintext.size, res, 0)
      doFinal(res, retLen)
      reset()
      return@with res
    }
    return encryptedBytes
  }

  fun decrypt(ciphertext: ByteArray, nonce: ByteArray): ByteArray {
    val cipher = inner.getOrSet { GCMBlockCipher(AESEngine()) }
    val key = AEADParameters(KEY, 128, nonce)
    val plainBytes = with(cipher) {
      cipher.init(false, key)
      val res = ByteArray(getOutputSize(ciphertext.size))
      val retLen = processBytes(ciphertext, 0, ciphertext.size, res, 0)
      doFinal(res, retLen)
      reset()
      return@with res
    }
    return plainBytes
  }
}

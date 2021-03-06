package org.meowcat.mesagisto.client

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.concurrent.getOrSet

object Cipher {
  private lateinit var key: KeyParameter
  lateinit var rawKey: String
  private val secureRandom: SecureRandom by lazy { SecureRandom() }

  private val inner: ThreadLocal<GCMBlockCipher> = ThreadLocal()
  fun newNonce(): ByteArray {
    val nonce = ByteArray(12)
    secureRandom.nextBytes(nonce)
    return nonce
  }
  fun init(key: String) {
    rawKey = key
    val digest = MessageDigest.getInstance("SHA-256")
    val key256 = digest.digest(key.toByteArray())
    this.key = KeyParameter(key256)
  }

  fun encrypt(plaintext: ByteArray, nonce: ByteArray): ByteArray {
    val cipher = inner.getOrSet { GCMBlockCipher(AESEngine()) }
    val key = AEADParameters(key, 128, nonce)
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
    val key = AEADParameters(key, 128, nonce)
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
  fun uniqueAddress(address: String): String {
    return "$address$rawKey"
  }
}

package org.meowcat.mesagisto.client

import org.bouncycastle.crypto.engines.AESEngine
import org.bouncycastle.crypto.modes.GCMBlockCipher
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.KeyParameter
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.properties.Delegates

object Cipher {
  private lateinit var KEY: KeyParameter
  internal lateinit var RAW_KEY: String
  private val SECURE_RANDOM: SecureRandom by lazy { SecureRandom() }
  var ENABLE by Delegates.notNull<Boolean>()
    private set
  var REFUSE_PLAIN by Delegates.notNull<Boolean>()
    private set
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
    val cipher = GCMBlockCipher(AESEngine())
    val parameters = AEADParameters(KEY, 128, nonce)
    cipher.init(true, parameters)
    val encryptedBytes = ByteArray(cipher.getOutputSize(plaintext.size))
    val retLen = cipher.processBytes(plaintext, 0, plaintext.size, encryptedBytes, 0)
    cipher.doFinal(encryptedBytes, retLen)
    return encryptedBytes
  }

  fun decrypt(ciphertext: ByteArray, nonce: ByteArray): ByteArray {
    val cipher = GCMBlockCipher(AESEngine())
    val parameters = AEADParameters(KEY, 128, nonce)
    cipher.init(false, parameters)
    val plainBytes = ByteArray(cipher.getOutputSize(ciphertext.size))
    val retLen = cipher.processBytes(ciphertext, 0, ciphertext.size, plainBytes, 0)
    cipher.doFinal(plainBytes, retLen)
    return plainBytes
  }
}

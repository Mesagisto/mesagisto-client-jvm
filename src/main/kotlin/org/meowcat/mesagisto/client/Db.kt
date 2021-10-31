@file:Suppress("NOTHING_TO_INLINE", "unused", "MemberVisibilityCanBePrivate")
package org.meowcat.mesagisto.client

import org.rocksdb.* // ktlint-disable no-wildcard-imports
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

object Db : AutoCloseable {
  private val imageUrlDb by lazy {
    val options = Options()
      .setCreateIfMissing(true)
    RocksDB.open(options, "db/$dbName/image_url_db")
  }
  private val midDbMap by lazy { ConcurrentHashMap<Int, RocksDB>() }

  private var dbName = "default"
  // please pay attention to thread-context classloader here
  fun init(dbName: String) = runCatching {
    this.dbName = dbName
    // rocksdb's classloader comes from its
    // specific class's(a field:NativeLibraryLoader) classloader
    RocksDB.loadLibrary()
  }
  fun putImageId(uid: String, fileId: String = "") =
    imageUrlDb.put(uid.toByteArray(), fileId.toByteArray())

  fun getImageId(uid: String): String? =
    imageUrlDb.get(uid.toByteArray())?.toString(charset = Charsets.UTF_8)

  fun putMsgId(
    target: ByteArray,
    uid: ByteArray,
    id: ByteArray,
    reverse: Boolean = true
  ) {
    val msgIdDb = midDbMap.getOrPut(target.contentHashCode()) {
      Logger.trace { "Message id db not found,creating a new one" }
      val options = Options()
        .setCreateIfMissing(true)
      Path("db/$dbName/msg-id").createDirectories()
      RocksDB.open(options, "db/$dbName/msg-id/${Base64.encodeToString(target)}")
    }
    msgIdDb.put(uid, id)
    if (reverse) {
      msgIdDb.put(id, uid)
    }
  }
  fun putMsgId(
    target: Long,
    uid: Int,
    id: Int,
    reverse: Boolean = true
  ) = putMsgId(target.toByteArray(), uid.toByteArray(), id.toByteArray(), reverse)
  fun putMsgId(
    target: Long,
    uid: ByteArray,
    id: Int,
    reverse: Boolean = true
  ) = putMsgId(target.toByteArray(), uid, id.toByteArray(), reverse)
  fun getMsgId(
    target: ByteArray,
    id: ByteArray
  ): ByteArray? {
    val msgIdDb = midDbMap[target.contentHashCode()] ?: return null
    return msgIdDb[id]
  }
  inline fun getMsgId(
    target: Long,
    id: Int
  ): ByteArray? = getMsgId(target.toByteArray(), id.toByteArray())

  inline fun getMsgId(
    target: Long,
    id: ByteArray
  ): ByteArray? = getMsgId(target.toByteArray(), id)

  inline fun getMsgIdAsI32(
    target: Long,
    id: ByteArray
  ): Int? = getMsgId(target, id)?.toI32()

  override fun close() {
    imageUrlDb.close()
    midDbMap.forEachValue(4) {
      it.close()
    }
    midDbMap.clear()
  }
}

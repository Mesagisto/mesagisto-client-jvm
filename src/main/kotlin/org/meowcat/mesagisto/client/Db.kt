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
  fun putImageId(uid: ByteArray, fileId: ByteArray = ByteArray(0)) =
    imageUrlDb.put(uid, fileId)

  fun getImageId(uid: ByteArray): ByteArray? = imageUrlDb.get(uid)

  fun putMsgId(
    target: ByteArray,
    uid: ByteArray,
    id: ByteArray,
    reverse: Boolean = true
  ) {
    val msgIdDb = midDbMap.getOrPut(target.contentHashCode()) {
      Logger.trace { "未发现消息ID数据库,正在创建..." }
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

  fun getMsgId(
    target: ByteArray,
    id: ByteArray
  ): ByteArray? {
    val msgIdDb = midDbMap[target.contentHashCode()] ?: return null
    return msgIdDb[id]
  }

  override fun close() {
    imageUrlDb.close()
    midDbMap.forEachValue(4) {
      it.close()
    }
    midDbMap.clear()
  }
}

@file:Suppress("unused", "MemberVisibilityCanBePrivate", "ktlint:no-wildcard-imports")

package org.mesagisto.client

import org.fusesource.leveldbjni.JniDBFactory.*
import org.fusesource.leveldbjni.internal.NativeDB
import org.iq80.leveldb.*
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.Path
import kotlin.io.path.createDirectories

object Db : AutoCloseable {
  private val imageUrlDb by lazy {
    val options = Options().createIfMissing(true)
    factory.open(File("db_v2/$name/image_url_db"), options)
  }
  private val midDbMap by lazy { ConcurrentHashMap<Int, DB>() }

  var name = "default"
  const val db_prefix = "db_v2"

  // please pay attention to thread-context classloader here
  fun init(dbName: String) = runCatching {
    name = dbName
    NativeDB.LIBRARY.load()
    File("db").deleteRecursively()
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
      val options = Options().createIfMissing(true)

      Path("$db_prefix/$name/msg-id").createDirectories()
      factory.open(File("$db_prefix/$name/msg-id/${Base64.encodeToString(target)}"), options)
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

@file:Suppress("unused", "MemberVisibilityCanBePrivate", "ktlint:no-wildcard-imports")

package org.mesagisto.client

import org.ktorm.database.Database
import org.ktorm.dsl.*
import org.ktorm.schema.Table
import org.ktorm.schema.bytes
import org.ktorm.support.sqlite.SQLiteDialect
import org.ktorm.support.sqlite.insertOrUpdate
import java.io.File

object MessageID : Table<Nothing>("message_id") {
  val id = bytes("id").primaryKey()
  val local = bytes("local")
  val remote = bytes("remote")
  fun createTable(database: Database) {
    database.useConnection { conn ->
      conn.prepareStatement(
        """
        CREATE TABLE IF NOT EXISTS message_id(
           id blob PRIMARY KEY NOT NULL,
           local blob NOT NULL,
           remote blob NOT NULL
        );
        """.trimIndent()
      ).execute()
    }
  }
}
object ImageDetail : Table<Nothing>("image_detail") {
  val id = bytes("id").primaryKey()
  val detail = bytes("detail")
  fun createTable(database: Database) {
    database.useConnection { conn ->
      conn.prepareStatement(
        """
        CREATE TABLE IF NOT EXISTS message_id(
           id blob PRIMARY KEY NOT NULL,
           detail blob NOT NULL,
        );
        """.trimIndent()
      ).execute()
    }
  }
}

object Db {
  private lateinit var database: Database
  var name = "default"
  const val db_prefix = "db_v3"

  fun init(dbName: String) = runCatching {
    name = dbName

    File("db").deleteRecursively()
    File("db_v2").deleteRecursively()
    File("$db_prefix/msgist-client").mkdirs()
    database = Database.connect("jdbc:sqlite:$db_prefix/msgist-client/$name.sqlite", dialect = SQLiteDialect())
    MessageID.createTable(database)
    ImageDetail.createTable(database)
  }
  fun putImageId(uid: ByteArray, fileId: ByteArray = ByteArray(0)) = database.insertOrUpdate(ImageDetail) {
    set(it.id, uid)
    set(it.detail, fileId)
    onConflict {
      set(it.detail, fileId)
    }
  }

  fun getImageId(uid: ByteArray) = database.from(ImageDetail)
    .select()
    .where { ImageDetail.id eq uid }
    .map { it[ImageDetail.detail] }
    .firstOrNull()

  fun putMsgId(
    target: ByteArray,
    remote: ByteArray,
    local: ByteArray
  ) = database.insertOrUpdate(MessageID) {
    set(it.id, target + local)
    set(it.local, local)
    set(it.remote, remote)
    onConflict {
      set(it.remote, remote)
    }
  }

  fun getMsgId(
    target: ByteArray,
    local: ByteArray
  ): ByteArray? = database.from(MessageID)
    .select()
    .where { MessageID.id eq (target + local) }
    .map { it[MessageID.remote] }
    .firstOrNull()
}

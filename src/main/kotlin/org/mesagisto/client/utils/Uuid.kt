@file:Suppress("NOTHING_TO_INLINE")

package org.mesagisto.client.utils // ktlint-disable filename

import com.fasterxml.uuid.Generators
import com.fasterxml.uuid.impl.NameBasedGenerator
import java.util.*

object UUIDv5 {
  private val NAMESPACE_MSGIST: UUID = UUID.fromString("179e3449-c41f-4a57-a763-59a787efaa52")
  val generator: ThreadLocal<NameBasedGenerator> = ThreadLocal.withInitial {
    Generators.nameBasedGenerator(NAMESPACE_MSGIST)
  }
  inline fun fromString(name: String): UUID = generator.get().generate(name)
  inline fun fromBytes(name: ByteArray): UUID = generator.get().generate(name)
}

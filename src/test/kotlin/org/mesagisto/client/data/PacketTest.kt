package org.mesagisto.client.data

import org.junit.jupiter.api.Test
import org.mesagisto.client.toHex
import java.util.*

internal class PacketTest {
  @Test
  fun test1() {
    val packet = Packet.newSub(UUID.randomUUID())
    val cbor = packet.toCbor()
    assert(
      Packet.fromCbor(cbor).onFailure {
        println(it)
      }.isSuccess
    )

    println(cbor.toHex())
  }
}

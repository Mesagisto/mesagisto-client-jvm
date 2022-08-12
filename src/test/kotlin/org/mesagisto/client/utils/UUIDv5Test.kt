package org.mesagisto.client.utils

import org.junit.jupiter.api.Assertions.* // ktlint-disable no-wildcard-imports
import org.junit.jupiter.api.Test

internal class UUIDv5Test {
  @Test
  fun test() {
    println("test ${UUIDv5.fromString("test")}")
  }
}

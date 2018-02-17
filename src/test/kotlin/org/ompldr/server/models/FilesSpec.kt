package org.ompldr.server.models

import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import org.ompldr.server.models.Files.fileIdDecode
import org.ompldr.server.models.Files.fileIdEncode
import java.util.Random
import kotlin.test.assertEquals

object FilesSpec : Spek({
  given("a fileId") {
    on("encode") {
      it("should always return the same result") {
        assertEquals("TB-VArnQ1Ula2ON5CtwoDw", fileIdEncode(1))
      }
    }
    on("decode") {
      it("should always return the same result") {
        assertEquals(1L, fileIdDecode("TB-VArnQ1Ula2ON5CtwoDw"))
      }
    }
    on("encoding and decoding random values") {
      it("should always match") {
        val random = Random()
        for (i in 1..10) {
          val number = random.nextLong()
          val encodedValue = fileIdEncode(number)
          assertEquals(encodedValue, fileIdEncode(number))
          assertEquals(number, fileIdDecode(encodedValue))
        }
      }
    }
  }
})

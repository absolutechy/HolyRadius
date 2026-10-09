package com.holyradius.nativecore.domain

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AtomicTextFileTest {
  @Test fun writeReadReplaceDelete() {
    val dir = Files.createTempDirectory("hr").toFile()
    val f = AtomicTextFile(File(dir, "session.json"))
    assertNull(f.read())
    f.write("one")
    f.write("two")
    assertEquals("two", f.read())
    f.delete()
    assertNull(f.read())
  }

  @Test fun leftoverTempFileIsIgnored() {
    val dir = Files.createTempDirectory("hr").toFile()
    val target = File(dir, "s.json")
    val f = AtomicTextFile(target)
    f.write("good")
    File(dir, "s.json.tmp").writeText("partial")
    assertEquals("good", f.read())
    assertFalse(File(dir, "s.json.tmp").exists())
  }
}

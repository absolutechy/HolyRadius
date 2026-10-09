package com.holyradius.nativecore.domain

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

/**
 * Crash-safe single-file text storage: write to a temp file, fsync, then rename over the target.
 * A reader sees either the old or the new content, never a partial write.
 */
class AtomicTextFile(private val file: File) {
  private val tmp = File(file.parentFile, file.name + ".tmp")

  fun read(): String? {
    if (tmp.exists()) tmp.delete() // leftover from a crash before rename; the target is still intact
    return if (file.exists()) file.readText(Charsets.UTF_8) else null
  }

  fun write(text: String) {
    file.parentFile?.mkdirs()
    FileOutputStream(tmp).use { out ->
      out.write(text.toByteArray(Charsets.UTF_8))
      out.fd.sync()
    }
    if (!tmp.renameTo(file)) {
      tmp.delete()
      throw IOException("Atomic rename failed for ${file.path}")
    }
  }

  fun delete() {
    tmp.delete()
    file.delete()
  }
}

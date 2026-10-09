package com.holyradius.nativecore.store

import android.content.Context
import com.holyradius.nativecore.domain.AtomicTextFile
import java.io.File

/** Process-wide lock guarding read-modify-write of the state files below. */
object StateLock

object StateFiles {
  private fun file(ctx: Context, name: String) = AtomicTextFile(File(File(ctx.filesDir, "holyradius"), name))

  fun registry(ctx: Context) = file(ctx, "registry.json")
  fun gate(ctx: Context) = file(ctx, "gate.json")
  fun evidence(ctx: Context) = file(ctx, "evidence.json")
  fun session(ctx: Context) = file(ctx, "session.json")
  fun config(ctx: Context) = file(ctx, "config.json")
}

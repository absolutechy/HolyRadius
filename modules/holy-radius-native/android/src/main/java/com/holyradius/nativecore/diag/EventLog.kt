package com.holyradius.nativecore.diag

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.holyradius.nativecore.util.ProcessState
import java.io.File
import org.json.JSONObject

/**
 * M1: append-only JSONL diagnostics log in filesDir/diag, rotated at [MAX_BYTES] (one generation).
 * Ids are derived from wall-clock time so they stay increasing across process restarts.
 */
object EventLog {
  const val TAG = "HolyRadius"
  private const val MAX_BYTES = 512 * 1024L
  private val lock = Any()
  private var lastId = 0L
  private var coldStartPending = true

  private fun dir(ctx: Context) = File(ctx.filesDir, "diag").apply { mkdirs() }
  private fun current(ctx: Context) = File(dir(ctx), "events.jsonl")
  private fun rotated(ctx: Context) = File(dir(ctx), "events.1.jsonl")

  fun log(ctx: Context, type: String, payload: Map<String, Any?> = emptyMap(), source: String = "native") {
    try {
      synchronized(lock) {
        val now = System.currentTimeMillis()
        lastId = maxOf(lastId + 1, now * 1000)
        val entry = JSONObject()
          .put("id", lastId)
          .put("t", now)
          .put("et", SystemClock.elapsedRealtime())
          .put("source", source)
          .put("type", type)
          .put("proc", ProcessState.current())
          .put("payload", JSONObject(payload))
        if (coldStartPending) {
          entry.put("coldStart", true)
          coldStartPending = false
        }
        val line = entry.toString()
        Log.i(TAG, line)
        val f = current(ctx)
        f.appendText(line + "\n")
        if (f.length() > MAX_BYTES) {
          rotated(ctx).delete()
          f.renameTo(rotated(ctx))
        }
      }
    } catch (e: Exception) {
      Log.e(TAG, "EventLog write failed", e)
    }
  }

  fun logJson(ctx: Context, source: String, type: String, payloadJson: String) {
    val payload = try {
      JSONObject(payloadJson).let { o -> o.keys().asSequence().associateWith { o.opt(it) } }
    } catch (e: Exception) {
      mapOf("raw" to payloadJson)
    }
    log(ctx, type, payload, source)
  }

  /** Entries with id > [sinceId], oldest first, at most [limit] (the newest ones if more exist). */
  fun read(ctx: Context, sinceId: Long, limit: Int): List<String> = synchronized(lock) {
    val lines = ArrayList<String>()
    for (f in listOf(rotated(ctx), current(ctx))) {
      if (!f.exists()) continue
      f.forEachLine { line ->
        val id = runCatching { JSONObject(line).optLong("id") }.getOrDefault(0L)
        if (id > sinceId) lines.add(line)
      }
    }
    if (lines.size > limit) lines.subList(lines.size - limit, lines.size).toList() else lines
  }

  fun clear(ctx: Context) = synchronized(lock) {
    current(ctx).delete()
    rotated(ctx).delete()
  }

  /** Concatenates both generations into a shareable file under cache/diag-export. */
  fun exportFile(ctx: Context): File = synchronized(lock) {
    val outDir = File(ctx.cacheDir, "diag-export").apply { mkdirs() }
    outDir.listFiles()?.forEach { it.delete() }
    val out = File(outDir, "holyradius-log-${System.currentTimeMillis()}.jsonl")
    out.bufferedWriter().use { w ->
      for (f in listOf(rotated(ctx), current(ctx))) {
        if (f.exists()) f.forEachLine { w.write(it); w.newLine() }
      }
    }
    out
  }
}

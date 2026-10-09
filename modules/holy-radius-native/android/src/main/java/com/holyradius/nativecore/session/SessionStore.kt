package com.holyradius.nativecore.session

import android.content.Context
import com.holyradius.nativecore.domain.Ownership
import com.holyradius.nativecore.domain.RingerMode
import com.holyradius.nativecore.domain.Session
import com.holyradius.nativecore.store.StateFiles
import org.json.JSONObject

/** Single atomic session record (temp file → fsync → rename). Callers hold StateLock. */
object SessionStore {
  fun load(ctx: Context): Session? {
    val text = StateFiles.session(ctx).read() ?: return null
    return try {
      val j = JSONObject(text)
      Session(
        sessionId = j.getString("sessionId"),
        fenceId = j.getString("fenceId"),
        previousMode = RingerMode.fromWire(j.getString("previousMode")),
        appliedMode = RingerMode.fromWire(j.getString("appliedMode")),
        startedAt = j.getLong("startedAt"),
        maxUntil = j.getLong("maxUntil"),
        ownership = Ownership.fromWire(j.getString("ownership")),
        expiryNotified = j.optBoolean("expiryNotified", false)
      )
    } catch (e: Exception) {
      null
    }
  }

  fun save(ctx: Context, s: Session) {
    StateFiles.session(ctx).write(toMap(s).let { JSONObject(it).toString() })
  }

  fun clear(ctx: Context) = StateFiles.session(ctx).delete()

  fun toMap(s: Session): Map<String, Any?> = mapOf(
    "sessionId" to s.sessionId,
    "fenceId" to s.fenceId,
    "previousMode" to s.previousMode.wire,
    "appliedMode" to s.appliedMode.wire,
    "startedAt" to s.startedAt,
    "maxUntil" to s.maxUntil,
    "ownership" to s.ownership.wire,
    "expiryNotified" to s.expiryNotified
  )
}

package com.holyradius.nativecore.store

import android.content.Context
import com.holyradius.nativecore.domain.Fix
import com.holyradius.nativecore.domain.GateState
import org.json.JSONArray
import org.json.JSONObject

/** Persists [GateState] (dedup/cooldown) and the last ENTER triggering location per fence. Callers hold [StateLock]. */
object GateStore {
  fun load(ctx: Context): GateState {
    val text = StateFiles.gate(ctx).read() ?: return GateState()
    return try {
      val j = JSONObject(text)
      GateState(
        lastEventAt = longMap(j.optJSONObject("lastEventAt")),
        lastVerifyAt = longMap(j.optJSONObject("lastVerifyAt")),
        verifyTimes = j.optJSONArray("verifyTimes")?.let { a -> List(a.length()) { a.getLong(it) } } ?: emptyList()
      )
    } catch (e: Exception) {
      GateState()
    }
  }

  fun save(ctx: Context, s: GateState) {
    val j = JSONObject()
      .put("lastEventAt", JSONObject(s.lastEventAt))
      .put("lastVerifyAt", JSONObject(s.lastVerifyAt))
      .put("verifyTimes", JSONArray(s.verifyTimes))
    StateFiles.gate(ctx).write(j.toString())
  }

  /** Stores a fix with its wall-clock capture time so its age can be recomputed later. */
  fun putEvidence(ctx: Context, fenceId: String, fix: Fix) {
    val all = loadEvidenceJson(ctx)
    all.put(
      fenceId,
      JSONObject()
        .put("lat", fix.lat)
        .put("lng", fix.lng)
        .put("acc", fix.accuracyM ?: JSONObject.NULL)
        .put("speed", fix.speedMps ?: JSONObject.NULL)
        .put("capturedAt", System.currentTimeMillis() - fix.ageMs)
    )
    StateFiles.evidence(ctx).write(all.toString())
  }

  fun getEvidence(ctx: Context, fenceId: String): Fix? {
    val e = loadEvidenceJson(ctx).optJSONObject(fenceId) ?: return null
    return Fix(
      lat = e.getDouble("lat"),
      lng = e.getDouble("lng"),
      accuracyM = if (e.isNull("acc")) null else e.getDouble("acc"),
      ageMs = System.currentTimeMillis() - e.getLong("capturedAt"),
      speedMps = if (e.isNull("speed")) null else e.getDouble("speed")
    )
  }

  private fun loadEvidenceJson(ctx: Context): JSONObject =
    StateFiles.evidence(ctx).read()?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()

  private fun longMap(o: JSONObject?): Map<String, Long> {
    if (o == null) return emptyMap()
    val out = HashMap<String, Long>()
    for (k in o.keys()) out[k] = o.getLong(k)
    return out
  }
}

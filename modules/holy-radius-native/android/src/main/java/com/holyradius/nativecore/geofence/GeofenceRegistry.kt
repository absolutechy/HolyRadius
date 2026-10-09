package com.holyradius.nativecore.geofence

import android.content.Context
import com.holyradius.nativecore.domain.RegistrySnapshot
import com.holyradius.nativecore.domain.RegistryStatus
import com.holyradius.nativecore.store.StateFiles
import com.holyradius.nativecore.store.StateLock
import org.json.JSONArray
import org.json.JSONObject

data class FenceSpec(
  val id: String,
  val lat: Double,
  val lng: Double,
  val radiusM: Double,
  val loiterMs: Long,
  val verifyRadiusM: Double
) {
  fun toJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("lat", lat)
    .put("lng", lng)
    .put("radiusM", radiusM)
    .put("loiterMs", loiterMs)
    .put("verifyRadiusM", verifyRadiusM)

  companion object {
    fun fromJson(j: JSONObject) = FenceSpec(
      id = j.getString("id"),
      lat = j.getDouble("lat"),
      lng = j.getDouble("lng"),
      radiusM = j.getDouble("radiusM"),
      loiterMs = j.getLong("loiterMs"),
      verifyRadiusM = j.getDouble("verifyRadiusM")
    )
  }
}

/** Persisted expected fence set plus the outcome of the last registration attempt. */
data class Registry(
  val fences: List<FenceSpec> = emptyList(),
  val status: RegistryStatus = RegistryStatus.NONE,
  val lastError: String? = null,
  val lastAttemptAt: Long? = null,
  val bootCount: Int? = null,
  val packageUpdateTime: Long? = null
) {
  fun snapshot() = RegistrySnapshot(fences.size, status, bootCount, packageUpdateTime)

  fun toJson(): JSONObject = JSONObject()
    .put("fences", JSONArray(fences.map { it.toJson() }))
    .put("status", status.wire)
    .put("lastError", lastError ?: JSONObject.NULL)
    .put("lastAttemptAt", lastAttemptAt ?: JSONObject.NULL)
    .put("bootCount", bootCount ?: JSONObject.NULL)
    .put("packageUpdateTime", packageUpdateTime ?: JSONObject.NULL)
}

object GeofenceRegistry {
  fun load(ctx: Context): Registry {
    val text = StateFiles.registry(ctx).read() ?: return Registry()
    return try {
      val j = JSONObject(text)
      val arr = j.optJSONArray("fences") ?: JSONArray()
      Registry(
        fences = List(arr.length()) { FenceSpec.fromJson(arr.getJSONObject(it)) },
        status = RegistryStatus.fromWire(j.optString("status")),
        lastError = if (j.isNull("lastError")) null else j.optString("lastError"),
        lastAttemptAt = if (j.isNull("lastAttemptAt")) null else j.optLong("lastAttemptAt"),
        bootCount = if (j.isNull("bootCount")) null else j.optInt("bootCount"),
        packageUpdateTime = if (j.isNull("packageUpdateTime")) null else j.optLong("packageUpdateTime")
      )
    } catch (e: Exception) {
      Registry()
    }
  }

  fun save(ctx: Context, reg: Registry) {
    StateFiles.registry(ctx).write(reg.toJson().toString())
  }

  fun update(ctx: Context, fn: (Registry) -> Registry): Registry = synchronized(StateLock) {
    val next = fn(load(ctx))
    save(ctx, next)
    next
  }

  fun find(ctx: Context, fenceId: String): FenceSpec? = load(ctx).fences.firstOrNull { it.id == fenceId }
}

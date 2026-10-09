package com.holyradius.nativecore.store

import android.content.Context
import com.holyradius.nativecore.config.PrototypeConfig
import org.json.JSONObject

/** Persists runtime overrides of [PrototypeConfig]; missing keys fall back to defaults. */
object ConfigStore {
  fun load(ctx: Context): PrototypeConfig {
    val text = StateFiles.config(ctx).read() ?: return PrototypeConfig.DEFAULT
    return try {
      fromJson(JSONObject(text))
    } catch (e: Exception) {
      PrototypeConfig.DEFAULT
    }
  }

  fun save(ctx: Context, cfg: PrototypeConfig) {
    synchronized(StateLock) { StateFiles.config(ctx).write(toJson(cfg).toString()) }
  }

  fun reset(ctx: Context) {
    synchronized(StateLock) { StateFiles.config(ctx).delete() }
  }

  fun toJson(c: PrototypeConfig) = JSONObject()
    .put("defaultRadiusM", c.defaultRadiusM)
    .put("defaultLoiterMs", c.defaultLoiterMs)
    .put("responsivenessMs", c.responsivenessMs)
    .put("defaultVerifyRadiusM", c.defaultVerifyRadiusM)
    .put("verifyOnEnter", c.verifyOnEnter)
    .put("verifyTimeoutMs", c.verifyTimeoutMs)
    .put("verifyMaxAttempts", c.verifyMaxAttempts)
    .put("maxFixAgeMs", c.maxFixAgeMs)
    .put("strongAccuracyM", c.strongAccuracyM)
    .put("hardMaxAccuracyM", c.hardMaxAccuracyM)
    .put("dedupWindowMs", c.dedupWindowMs)
    .put("verifyCooldownMs", c.verifyCooldownMs)
    .put("maxVerificationsPerHour", c.maxVerificationsPerHour)
    .put("maxSessionMs", c.maxSessionMs)

  fun fromJson(j: JSONObject): PrototypeConfig {
    val d = PrototypeConfig.DEFAULT
    return PrototypeConfig(
      defaultRadiusM = j.optDouble("defaultRadiusM", d.defaultRadiusM),
      defaultLoiterMs = j.optLong("defaultLoiterMs", d.defaultLoiterMs),
      responsivenessMs = j.optLong("responsivenessMs", d.responsivenessMs),
      defaultVerifyRadiusM = j.optDouble("defaultVerifyRadiusM", d.defaultVerifyRadiusM),
      verifyOnEnter = j.optBoolean("verifyOnEnter", d.verifyOnEnter),
      verifyTimeoutMs = j.optLong("verifyTimeoutMs", d.verifyTimeoutMs),
      verifyMaxAttempts = j.optInt("verifyMaxAttempts", d.verifyMaxAttempts),
      maxFixAgeMs = j.optLong("maxFixAgeMs", d.maxFixAgeMs),
      strongAccuracyM = j.optDouble("strongAccuracyM", d.strongAccuracyM),
      hardMaxAccuracyM = j.optDouble("hardMaxAccuracyM", d.hardMaxAccuracyM),
      dedupWindowMs = j.optLong("dedupWindowMs", d.dedupWindowMs),
      verifyCooldownMs = j.optLong("verifyCooldownMs", d.verifyCooldownMs),
      maxVerificationsPerHour = j.optInt("maxVerificationsPerHour", d.maxVerificationsPerHour),
      maxSessionMs = j.optLong("maxSessionMs", d.maxSessionMs)
    )
  }
}

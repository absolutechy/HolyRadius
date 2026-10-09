package com.holyradius.nativecore.config

/**
 * Centralized Phase 1 defaults. These are starting values to MEASURE, not tuned values.
 * Mirrored in src/config/prototype.ts; overridable at runtime from the diagnostics screen.
 */
data class PrototypeConfig(
  val defaultRadiusM: Double = 150.0,
  val defaultLoiterMs: Long = 90_000,
  val responsivenessMs: Long = 30_000,
  val defaultVerifyRadiusM: Double = 30.0,
  val verifyOnEnter: Boolean = false,
  val verifyTimeoutMs: Long = 20_000,
  val verifyMaxAttempts: Int = 2,
  val maxFixAgeMs: Long = 30_000,
  val strongAccuracyM: Double = 35.0,
  val hardMaxAccuracyM: Double = 120.0,
  val dedupWindowMs: Long = 60_000,
  val verifyCooldownMs: Long = 600_000,
  val maxVerificationsPerHour: Int = 6,
  val maxSessionMs: Long = 3 * 60 * 60 * 1000L
) {
  companion object {
    val DEFAULT = PrototypeConfig()
  }
}

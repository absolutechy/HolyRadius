package com.holyradius.nativecore.domain

import com.holyradius.nativecore.config.PrototypeConfig

/** A location fix reduced to what the decision logic needs. [ageMs] is computed by the caller. */
data class Fix(
  val lat: Double,
  val lng: Double,
  val accuracyM: Double?,
  val ageMs: Long,
  val speedMps: Double? = null
)

sealed interface FixCheck {
  val reason: String

  data class Valid(val fix: Fix) : FixCheck {
    override val reason = "valid"
  }

  data object Missing : FixCheck {
    override val reason = "no_fix"
  }

  data class Stale(val ageMs: Long) : FixCheck {
    override val reason = "stale"
  }

  data object NoAccuracy : FixCheck {
    override val reason = "no_accuracy"
  }

  data class TooInaccurate(val accuracyM: Double) : FixCheck {
    override val reason = "too_inaccurate"
  }
}

object FixValidator {
  fun check(fix: Fix?, cfg: PrototypeConfig): FixCheck {
    if (fix == null) return FixCheck.Missing
    if (fix.ageMs < 0 || fix.ageMs > cfg.maxFixAgeMs) return FixCheck.Stale(fix.ageMs)
    val acc = fix.accuracyM ?: return FixCheck.NoAccuracy
    if (acc.isNaN() || acc <= 0.0) return FixCheck.NoAccuracy
    if (acc > cfg.hardMaxAccuracyM) return FixCheck.TooInaccurate(acc)
    return FixCheck.Valid(fix)
  }
}

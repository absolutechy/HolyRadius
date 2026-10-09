package com.holyradius.nativecore.domain

import com.holyradius.nativecore.config.PrototypeConfig

enum class Confidence { STRONG, UNCERTAIN, INSUFFICIENT }

enum class EvidenceSource(val wire: String) {
  /** A fresh fix requested by VerificationWorker. */
  VERIFIED_FIX("verified_fix"),

  /** Only GeofencingEvent.triggeringLocation; never enough on its own for an automatic change. */
  TRIGGERING_LOCATION("triggering_location")
}

data class Decision(
  val confidence: Confidence,
  val reason: String,
  val distanceM: Double? = null,
  val accuracyM: Double? = null
)

/**
 * Phase 1 prototype rule (replaced by PresenceEngine in Phase 2):
 *  STRONG      valid fix, distance <= verifyRadius and accuracy <= strongAccuracy
 *  UNCERTAIN   the accuracy circle reaches the verify radius (distance - accuracy <= verifyRadius)
 *  INSUFFICIENT anything else, or no valid fix
 * Evidence from the triggering location alone is capped at UNCERTAIN.
 */
object PrototypeDecision {
  fun decide(
    fix: Fix?,
    source: EvidenceSource,
    targetLat: Double,
    targetLng: Double,
    verifyRadiusM: Double,
    cfg: PrototypeConfig
  ): Decision {
    val check = FixValidator.check(fix, cfg)
    if (check !is FixCheck.Valid) return Decision(Confidence.INSUFFICIENT, check.reason)

    val f = check.fix
    val acc = f.accuracyM!!
    val d = Geo.distanceM(f.lat, f.lng, targetLat, targetLng)

    val raw = when {
      d <= verifyRadiusM && acc <= cfg.strongAccuracyM -> Decision(Confidence.STRONG, "inside_accurate", d, acc)
      d - acc <= verifyRadiusM -> Decision(Confidence.UNCERTAIN, "possibly_inside", d, acc)
      else -> Decision(Confidence.INSUFFICIENT, "outside", d, acc)
    }
    if (raw.confidence == Confidence.STRONG && source == EvidenceSource.TRIGGERING_LOCATION) {
      return raw.copy(confidence = Confidence.UNCERTAIN, reason = "triggering_location_only")
    }
    return raw
  }
}

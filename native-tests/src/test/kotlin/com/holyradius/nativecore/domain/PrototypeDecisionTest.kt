package com.holyradius.nativecore.domain

import com.holyradius.nativecore.config.PrototypeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PrototypeDecisionTest {
  private val cfg = PrototypeConfig.DEFAULT
  private val lat = 51.5
  private val lng = -0.1

  /** Point [metresNorth] metres north of the target. */
  private fun fixAt(metresNorth: Double, acc: Double?, ageMs: Long = 1_000) =
    Fix(lat + metresNorth / 111_195.0, lng, acc, ageMs)

  private fun decide(fix: Fix?, source: EvidenceSource = EvidenceSource.VERIFIED_FIX) =
    PrototypeDecision.decide(fix, source, lat, lng, 30.0, cfg)

  @Test fun haversineIsSane() {
    val d = Geo.distanceM(lat, lng, lat + 100 / 111_195.0, lng)
    assertTrue("got $d", d in 99.0..101.0)
  }

  @Test fun accurateFixInsideIsStrong() {
    assertEquals(Confidence.STRONG, decide(fixAt(10.0, 12.0)).confidence)
  }

  @Test fun inaccurateFixInsideIsOnlyUncertain() {
    val d = decide(fixAt(10.0, 60.0))
    assertEquals(Confidence.UNCERTAIN, d.confidence)
  }

  @Test fun accuracyCircleReachingRadiusIsUncertain() {
    assertEquals(Confidence.UNCERTAIN, decide(fixAt(60.0, 40.0)).confidence)
  }

  @Test fun farOutsideIsInsufficient() {
    val d = decide(fixAt(200.0, 20.0))
    assertEquals(Confidence.INSUFFICIENT, d.confidence)
    assertEquals("outside", d.reason)
  }

  @Test fun triggeringLocationAloneIsCappedAtUncertain() {
    val d = decide(fixAt(5.0, 10.0), EvidenceSource.TRIGGERING_LOCATION)
    assertEquals(Confidence.UNCERTAIN, d.confidence)
    assertEquals("triggering_location_only", d.reason)
  }

  @Test fun staleOrMissingOrHugeAccuracyIsInsufficient() {
    assertEquals("stale", decide(fixAt(5.0, 10.0, ageMs = 120_000)).reason)
    assertEquals("no_fix", decide(null).reason)
    assertEquals("no_accuracy", decide(fixAt(5.0, null)).reason)
    assertEquals("too_inaccurate", decide(fixAt(5.0, 500.0)).reason)
  }
}

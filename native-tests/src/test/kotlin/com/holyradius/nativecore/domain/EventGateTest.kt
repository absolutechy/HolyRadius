package com.holyradius.nativecore.domain

import com.holyradius.nativecore.config.PrototypeConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventGateTest {
  private val cfg = PrototypeConfig(dedupWindowMs = 60_000, verifyCooldownMs = 600_000, maxVerificationsPerHour = 2)

  @Test fun duplicateWithinWindowIsDropped() {
    val (s1, dup1) = EventGate.onTransition(GateState(), "a", Transition.DWELL, 1_000, cfg)
    assertFalse(dup1)
    val (_, dup2) = EventGate.onTransition(s1, "a", Transition.DWELL, 30_000, cfg)
    assertTrue(dup2)
    val (_, dup3) = EventGate.onTransition(s1, "a", Transition.EXIT, 30_000, cfg)
    assertFalse("different transition is not a duplicate", dup3)
    val (_, dup4) = EventGate.onTransition(s1, "a", Transition.DWELL, 70_000, cfg)
    assertFalse("outside window", dup4)
  }

  @Test fun perFenceCooldown() {
    val (s1, r1) = EventGate.tryStartVerification(GateState(), "a", 0, cfg)
    assertEquals(VerifyGate.Accept, r1)
    val (_, r2) = EventGate.tryStartVerification(s1, "a", 60_000, cfg)
    assertTrue(r2 is VerifyGate.CoolingDown)
    val (_, r3) = EventGate.tryStartVerification(s1, "b", 60_000, cfg)
    assertEquals(VerifyGate.Accept, r3)
  }

  @Test fun hourlyCapAcrossFences() {
    var s = GateState()
    s = EventGate.tryStartVerification(s, "a", 0, cfg).first
    s = EventGate.tryStartVerification(s, "b", 1_000, cfg).first
    val (s2, r) = EventGate.tryStartVerification(s, "c", 2_000, cfg)
    assertEquals(VerifyGate.HourlyCapReached, r)
    val (_, later) = EventGate.tryStartVerification(s2, "c", 3_700_000, cfg)
    assertEquals(VerifyGate.Accept, later)
  }

  @Test fun clockGoingBackwardsDoesNotBlockForever() {
    val (s1, _) = EventGate.tryStartVerification(GateState(), "a", 10_000_000, cfg)
    val (_, r) = EventGate.tryStartVerification(s1, "a", 5_000, cfg)
    assertEquals(VerifyGate.Accept, r)
  }
}

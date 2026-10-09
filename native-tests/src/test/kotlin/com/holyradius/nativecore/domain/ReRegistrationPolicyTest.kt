package com.holyradius.nativecore.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReRegistrationPolicyTest {
  private val reg = RegistrySnapshot(expectedCount = 2, status = RegistryStatus.REGISTERED, bootCount = 7, packageUpdateTime = 100)
  private val dev = DeviceSnapshot(bootCount = 7, packageUpdateTime = 100, locationEnabled = true, hasRequiredPermissions = true)

  @Test fun upToDate() {
    assertTrue(ReRegistrationPolicy.decide(reg, dev) is ReRegDecision.NotNeeded)
  }

  @Test fun noFencesNeverRegisters() {
    assertEquals("no_fences", ReRegistrationPolicy.decide(reg.copy(expectedCount = 0), dev).reason)
  }

  @Test fun rebootUpdateAndFailedStatusTrigger() {
    assertEquals("rebooted", ReRegistrationPolicy.decide(reg, dev.copy(bootCount = 8)).reason)
    assertEquals("package_updated", ReRegistrationPolicy.decide(reg, dev.copy(packageUpdateTime = 200)).reason)
    assertEquals(
      "status_not_available",
      ReRegistrationPolicy.decide(reg.copy(status = RegistryStatus.NOT_AVAILABLE), dev).reason
    )
  }

  @Test fun blockedWithoutPermissionsOrLocation() {
    val stale = reg.copy(status = RegistryStatus.FAILED)
    assertTrue(ReRegistrationPolicy.decide(stale, dev.copy(hasRequiredPermissions = false)) is ReRegDecision.Blocked)
    assertEquals("location_off", ReRegistrationPolicy.decide(stale, dev.copy(locationEnabled = false)).reason)
  }
}

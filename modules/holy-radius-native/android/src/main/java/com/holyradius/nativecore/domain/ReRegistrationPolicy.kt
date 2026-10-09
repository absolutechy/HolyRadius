package com.holyradius.nativecore.domain

enum class RegistryStatus(val wire: String) {
  NONE("none"),
  REGISTERED("registered"),
  FAILED("failed"),
  NOT_AVAILABLE("not_available"),
  SKIPPED_PRECHECK("skipped_precheck");

  companion object {
    fun fromWire(value: String?) = entries.firstOrNull { it.wire == value } ?: NONE
  }
}

data class RegistrySnapshot(
  val expectedCount: Int,
  val status: RegistryStatus,
  val bootCount: Int?,
  val packageUpdateTime: Long?
)

data class DeviceSnapshot(
  val bootCount: Int?,
  val packageUpdateTime: Long,
  val locationEnabled: Boolean,
  val hasRequiredPermissions: Boolean
)

sealed interface ReRegDecision {
  val reason: String

  data class NotNeeded(override val reason: String) : ReRegDecision

  data class Needed(override val reason: String) : ReRegDecision

  /** Registration is needed but cannot succeed right now; do not retry until conditions change. */
  data class Blocked(override val reason: String) : ReRegDecision
}

/** Decides at app launch (or any trigger) whether geofences must be registered again. */
object ReRegistrationPolicy {
  fun decide(reg: RegistrySnapshot, dev: DeviceSnapshot): ReRegDecision {
    if (reg.expectedCount == 0) return ReRegDecision.NotNeeded("no_fences")
    val reason = when {
      reg.status != RegistryStatus.REGISTERED -> "status_${reg.status.wire}"
      reg.bootCount != null && dev.bootCount != null && reg.bootCount != dev.bootCount -> "rebooted"
      reg.packageUpdateTime != null && reg.packageUpdateTime != dev.packageUpdateTime -> "package_updated"
      else -> return ReRegDecision.NotNeeded("up_to_date")
    }
    if (!dev.hasRequiredPermissions) return ReRegDecision.Blocked("permissions_missing")
    if (!dev.locationEnabled) return ReRegDecision.Blocked("location_off")
    return ReRegDecision.Needed(reason)
  }
}

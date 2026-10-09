package com.holyradius.nativecore.domain

import com.holyradius.nativecore.config.PrototypeConfig

enum class Transition(val wire: String) { ENTER("enter"), DWELL("dwell"), EXIT("exit") }

/** Persisted gate state; timestamps are wall-clock ms so they survive reboots. */
data class GateState(
  val lastEventAt: Map<String, Long> = emptyMap(),
  val lastVerifyAt: Map<String, Long> = emptyMap(),
  val verifyTimes: List<Long> = emptyList()
)

sealed interface VerifyGate {
  val wire: String

  data object Accept : VerifyGate {
    override val wire = "accept"
  }

  data class CoolingDown(val remainingMs: Long) : VerifyGate {
    override val wire = "cooling_down"
  }

  data object HourlyCapReached : VerifyGate {
    override val wire = "hourly_cap"
  }
}

/** Deduplication, per-fence cooldown and a global hourly cap on verification attempts. */
object EventGate {
  private const val HOUR_MS = 60 * 60 * 1000L

  private fun key(fenceId: String, t: Transition) = "$fenceId|${t.wire}"

  /** Returns the new state and whether the event is a duplicate (and should be ignored). */
  fun onTransition(
    state: GateState,
    fenceId: String,
    transition: Transition,
    nowMs: Long,
    cfg: PrototypeConfig
  ): Pair<GateState, Boolean> {
    val k = key(fenceId, transition)
    val last = state.lastEventAt[k]
    val duplicate = last != null && nowMs - last in 0 until cfg.dedupWindowMs
    if (duplicate) return state to true
    val pruned = state.lastEventAt.filterValues { nowMs - it < maxOf(cfg.dedupWindowMs, HOUR_MS) }
    return state.copy(lastEventAt = pruned + (k to nowMs)) to false
  }

  /** Checks cooldown/cap; on Accept the returned state records the attempt. */
  fun tryStartVerification(
    state: GateState,
    fenceId: String,
    nowMs: Long,
    cfg: PrototypeConfig
  ): Pair<GateState, VerifyGate> {
    val recent = state.verifyTimes.filter { nowMs - it in 0 until HOUR_MS }
    val last = state.lastVerifyAt[fenceId]
    if (last != null && nowMs - last in 0 until cfg.verifyCooldownMs) {
      return state.copy(verifyTimes = recent) to VerifyGate.CoolingDown(cfg.verifyCooldownMs - (nowMs - last))
    }
    if (recent.size >= cfg.maxVerificationsPerHour) {
      return state.copy(verifyTimes = recent) to VerifyGate.HourlyCapReached
    }
    return state.copy(
      lastVerifyAt = state.lastVerifyAt + (fenceId to nowMs),
      verifyTimes = recent + nowMs
    ) to VerifyGate.Accept
  }
}

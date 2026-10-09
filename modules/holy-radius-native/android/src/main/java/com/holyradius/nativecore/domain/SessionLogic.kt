package com.holyradius.nativecore.domain

enum class Ownership(val wire: String) {
  /** Written before the ringer call; resolved by recover() if the process dies mid-apply. */
  PENDING("pending"),

  /** HolyRadius changed the mode and verified it; only this state may restore. */
  OWNED("owned"),

  /** The user changed the mode while the session was active; HolyRadius will not touch it. */
  RELINQUISHED("relinquished"),

  ENDED("ended");

  companion object {
    fun fromWire(value: String) = entries.first { it.wire == value }
  }
}

data class Session(
  val sessionId: String,
  val fenceId: String,
  val previousMode: RingerMode,
  val appliedMode: RingerMode,
  val startedAt: Long,
  val maxUntil: Long,
  val ownership: Ownership,
  val expiryNotified: Boolean = false
)

sealed interface ApplyPlan {
  /** An OWNED or PENDING session already exists; never stack sessions. */
  data class AlreadyActive(val session: Session) : ApplyPlan

  /** Phone is already at least as quiet as the target; create no session, so nothing is restored later. */
  data class AlreadyQuiet(val current: RingerMode) : ApplyPlan

  /** Persist [pending] first, then change the mode. */
  data class Proceed(val pending: Session) : ApplyPlan
}

sealed interface ApplyOutcome {
  data class Owned(val session: Session) : ApplyOutcome

  /** Delete the pending record and fall back to a manual-action notification. */
  data class Abort(val reason: String) : ApplyOutcome
}

sealed interface RestorePlan {
  data object NoSession : RestorePlan

  data class NotOwned(val ownership: Ownership) : RestorePlan

  /** The current mode is no longer what we applied: the user changed it. Leave it alone. */
  data class Relinquish(val session: Session) : RestorePlan

  data class Restore(val session: Session, val target: RingerMode) : RestorePlan
}

sealed interface RestoreOutcome {
  data class Ended(val session: Session) : RestoreOutcome

  /** Restore failed; keep ownership so a later attempt / manual action can finish it. */
  data class StillOwned(val session: Session, val reason: String) : RestoreOutcome
}

sealed interface RecoverPlan {
  data object Nothing : RecoverPlan

  data class PromotePending(val session: Session) : RecoverPlan

  data class DiscardPending(val session: Session) : RecoverPlan

  data class Relinquish(val session: Session) : RecoverPlan

  /** Max duration passed: ask the user; never blindly turn sound back on. */
  data class NotifyExpired(val session: Session) : RecoverPlan

  /** RELINQUISHED / ENDED records are cleared. */
  data class Clear(val session: Session) : RecoverPlan
}

/** Pure decisions for the ringer session lifecycle. All persistence/IO is done by the caller. */
object SessionLogic {
  fun planApply(
    existing: Session?,
    current: RingerMode,
    target: RingerMode,
    fenceId: String,
    nowMs: Long,
    maxSessionMs: Long,
    newId: () -> String
  ): ApplyPlan {
    if (existing != null && (existing.ownership == Ownership.OWNED || existing.ownership == Ownership.PENDING)) {
      return ApplyPlan.AlreadyActive(existing)
    }
    if (current.quietness >= target.quietness) return ApplyPlan.AlreadyQuiet(current)
    return ApplyPlan.Proceed(
      Session(
        sessionId = newId(),
        fenceId = fenceId,
        previousMode = current,
        appliedMode = target,
        startedAt = nowMs,
        maxUntil = nowMs + maxSessionMs,
        ownership = Ownership.PENDING
      )
    )
  }

  fun onApplyResult(pending: Session, result: RingerSetResult): ApplyOutcome = when (result) {
    is RingerSetResult.Ok ->
      if (result.observed == pending.appliedMode) {
        ApplyOutcome.Owned(pending.copy(ownership = Ownership.OWNED))
      } else {
        ApplyOutcome.Abort("observed_${result.observed.wire}")
      }
    else -> ApplyOutcome.Abort(result.wire)
  }

  fun planRestore(session: Session?, current: RingerMode): RestorePlan {
    if (session == null) return RestorePlan.NoSession
    if (session.ownership != Ownership.OWNED) return RestorePlan.NotOwned(session.ownership)
    if (current != session.appliedMode) {
      return RestorePlan.Relinquish(session.copy(ownership = Ownership.RELINQUISHED))
    }
    return RestorePlan.Restore(session, session.previousMode)
  }

  fun onRestoreResult(session: Session, result: RingerSetResult): RestoreOutcome =
    if (result is RingerSetResult.Ok && result.observed == session.previousMode) {
      RestoreOutcome.Ended(session.copy(ownership = Ownership.ENDED))
    } else {
      RestoreOutcome.StillOwned(session, result.wire)
    }

  fun planRecover(session: Session?, current: RingerMode, nowMs: Long): RecoverPlan {
    if (session == null) return RecoverPlan.Nothing
    return when (session.ownership) {
      Ownership.PENDING ->
        if (current == session.appliedMode) {
          RecoverPlan.PromotePending(session.copy(ownership = Ownership.OWNED))
        } else {
          RecoverPlan.DiscardPending(session)
        }
      Ownership.OWNED -> when {
        current != session.appliedMode -> RecoverPlan.Relinquish(session.copy(ownership = Ownership.RELINQUISHED))
        nowMs >= session.maxUntil && !session.expiryNotified ->
          RecoverPlan.NotifyExpired(session.copy(expiryNotified = true))
        else -> RecoverPlan.Nothing
      }
      Ownership.RELINQUISHED, Ownership.ENDED -> RecoverPlan.Clear(session)
    }
  }
}

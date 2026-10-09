package com.holyradius.nativecore.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionLogicTest {
  private val max = 3 * 60 * 60 * 1000L

  private fun plan(existing: Session?, current: RingerMode) =
    SessionLogic.planApply(existing, current, RingerMode.VIBRATE, "f1", 1_000, max) { "s1" }

  private fun owned() = (plan(null, RingerMode.NORMAL) as ApplyPlan.Proceed).pending.copy(ownership = Ownership.OWNED)

  @Test fun applyFromNormalCreatesPendingSession() {
    val p = plan(null, RingerMode.NORMAL)
    assertTrue(p is ApplyPlan.Proceed)
    val s = (p as ApplyPlan.Proceed).pending
    assertEquals(Ownership.PENDING, s.ownership)
    assertEquals(RingerMode.NORMAL, s.previousMode)
    assertEquals(1_000 + max, s.maxUntil)
  }

  @Test fun alreadyVibrateOrSilentCreatesNoSession() {
    assertTrue(plan(null, RingerMode.VIBRATE) is ApplyPlan.AlreadyQuiet)
    assertTrue("never make SILENT louder", plan(null, RingerMode.SILENT) is ApplyPlan.AlreadyQuiet)
  }

  @Test fun neverStackSessions() {
    assertTrue(plan(owned(), RingerMode.NORMAL) is ApplyPlan.AlreadyActive)
  }

  @Test fun applyOwnedOnlyWhenVerified() {
    val pending = (plan(null, RingerMode.NORMAL) as ApplyPlan.Proceed).pending
    assertTrue(SessionLogic.onApplyResult(pending, RingerSetResult.Ok(RingerMode.VIBRATE)) is ApplyOutcome.Owned)
    assertTrue(SessionLogic.onApplyResult(pending, RingerSetResult.NeedsPolicyAccess) is ApplyOutcome.Abort)
    assertTrue(
      SessionLogic.onApplyResult(pending, RingerSetResult.Mismatch(RingerMode.VIBRATE, RingerMode.NORMAL)) is ApplyOutcome.Abort
    )
  }

  @Test fun noSessionMeansNoRestore() {
    assertEquals(RestorePlan.NoSession, SessionLogic.planRestore(null, RingerMode.VIBRATE))
  }

  @Test fun pendingSessionIsNotRestored() {
    val pending = (plan(null, RingerMode.NORMAL) as ApplyPlan.Proceed).pending
    assertTrue(SessionLogic.planRestore(pending, RingerMode.VIBRATE) is RestorePlan.NotOwned)
  }

  @Test fun manualChangeRelinquishes() {
    val r = SessionLogic.planRestore(owned(), RingerMode.NORMAL)
    assertTrue(r is RestorePlan.Relinquish)
    assertEquals(Ownership.RELINQUISHED, (r as RestorePlan.Relinquish).session.ownership)
  }

  @Test fun ownedAndUnchangedRestoresPrevious() {
    val r = SessionLogic.planRestore(owned(), RingerMode.VIBRATE)
    assertEquals(RingerMode.NORMAL, (r as RestorePlan.Restore).target)
  }

  @Test fun failedRestoreKeepsOwnership() {
    val s = owned()
    assertTrue(SessionLogic.onRestoreResult(s, RingerSetResult.Ok(RingerMode.NORMAL)) is RestoreOutcome.Ended)
    assertTrue(SessionLogic.onRestoreResult(s, RingerSetResult.Failed("x")) is RestoreOutcome.StillOwned)
  }

  @Test fun recoverPendingAfterCrash() {
    val pending = (plan(null, RingerMode.NORMAL) as ApplyPlan.Proceed).pending
    assertTrue(SessionLogic.planRecover(pending, RingerMode.VIBRATE, 2_000) is RecoverPlan.PromotePending)
    assertTrue(SessionLogic.planRecover(pending, RingerMode.NORMAL, 2_000) is RecoverPlan.DiscardPending)
  }

  @Test fun expiredSessionNotifiesOnceAndNeverRestoresBlindly() {
    val s = owned()
    val r = SessionLogic.planRecover(s, RingerMode.VIBRATE, s.maxUntil + 1)
    assertTrue(r is RecoverPlan.NotifyExpired)
    val notified = (r as RecoverPlan.NotifyExpired).session
    assertEquals(RecoverPlan.Nothing, SessionLogic.planRecover(notified, RingerMode.VIBRATE, s.maxUntil + 2))
  }

  @Test fun recoverDetectsManualChangeWhileProcessDead() {
    assertTrue(SessionLogic.planRecover(owned(), RingerMode.NORMAL, 2_000) is RecoverPlan.Relinquish)
  }

  @Test fun finishedRecordsAreCleared() {
    val ended = owned().copy(ownership = Ownership.ENDED)
    assertTrue(SessionLogic.planRecover(ended, RingerMode.NORMAL, 2_000) is RecoverPlan.Clear)
  }
}

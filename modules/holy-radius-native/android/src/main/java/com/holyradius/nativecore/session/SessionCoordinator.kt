package com.holyradius.nativecore.session

import android.content.Context
import com.holyradius.nativecore.diag.EventLog
import com.holyradius.nativecore.domain.ApplyOutcome
import com.holyradius.nativecore.domain.ApplyPlan
import com.holyradius.nativecore.domain.Ownership
import com.holyradius.nativecore.domain.RecoverPlan
import com.holyradius.nativecore.domain.RestoreOutcome
import com.holyradius.nativecore.domain.RestorePlan
import com.holyradius.nativecore.domain.RingerMode
import com.holyradius.nativecore.domain.Session
import com.holyradius.nativecore.domain.SessionLogic
import com.holyradius.nativecore.notify.Notifier
import com.holyradius.nativecore.recovery.SessionExpiryWorker
import com.holyradius.nativecore.ringer.RingerController
import com.holyradius.nativecore.store.ConfigStore
import com.holyradius.nativecore.store.StateLock
import java.util.UUID

/**
 * M8: executes SessionLogic decisions. All read-modify-write happens under StateLock, and the
 * PENDING record is written before the ringer is touched so a crash can be recovered.
 */
class SessionCoordinator(private val ctx: Context) {
  private val ringer = RingerController(ctx)

  fun current(): Session? = synchronized(StateLock) { SessionStore.load(ctx) }

  fun apply(fenceId: String, trigger: String, target: RingerMode = RingerMode.VIBRATE): Map<String, Any?> =
    synchronized(StateLock) {
      val cfg = ConfigStore.load(ctx)
      val existing = SessionStore.load(ctx)
      val plan = SessionLogic.planApply(
        existing, ringer.current(), target, fenceId, System.currentTimeMillis(), cfg.maxSessionMs
      ) { UUID.randomUUID().toString() }

      val out: Map<String, Any?> = when (plan) {
        is ApplyPlan.AlreadyActive -> mapOf("result" to "already_active", "sessionId" to plan.session.sessionId)
        is ApplyPlan.AlreadyQuiet -> mapOf("result" to "already_quiet", "current" to plan.current.wire)
        is ApplyPlan.Proceed -> {
          SessionStore.save(ctx, plan.pending)
          val setResult = ringer.setMode(target, trigger)
          when (val outcome = SessionLogic.onApplyResult(plan.pending, setResult)) {
            is ApplyOutcome.Owned -> {
              SessionStore.save(ctx, outcome.session)
              SessionExpiryWorker.schedule(ctx, outcome.session.maxUntil - System.currentTimeMillis())
              Notifier.sessionActive(ctx, outcome.session)
              mapOf("result" to "owned", "session" to SessionStore.toMap(outcome.session))
            }
            is ApplyOutcome.Abort -> {
              SessionStore.clear(ctx)
              Notifier.manualActionNeeded(ctx, fenceId, outcome.reason)
              mapOf("result" to "aborted", "reason" to outcome.reason)
            }
          }
        }
      }
      EventLog.log(ctx, "session_apply", mapOf("fenceId" to fenceId, "trigger" to trigger) + out)
      out
    }

  /** EXIT of a fence: only the fence that started the session may end it. */
  fun onExit(fenceId: String): Map<String, Any?> = synchronized(StateLock) {
    val s = SessionStore.load(ctx)
    if (s != null && s.fenceId != fenceId) {
      val out = mapOf("result" to "other_fence", "sessionFence" to s.fenceId)
      EventLog.log(ctx, "session_restore", mapOf("fenceId" to fenceId, "trigger" to "exit") + out)
      return@synchronized out
    }
    restoreLocked("exit")
  }

  fun manualRestore(trigger: String): Map<String, Any?> = synchronized(StateLock) { restoreLocked(trigger) }

  private fun restoreLocked(trigger: String): Map<String, Any?> {
    val out: Map<String, Any?> = when (val plan = SessionLogic.planRestore(SessionStore.load(ctx), ringer.current())) {
      RestorePlan.NoSession -> mapOf("result" to "no_session")
      is RestorePlan.NotOwned -> mapOf("result" to "not_owned", "ownership" to plan.ownership.wire)
      is RestorePlan.Relinquish -> {
        SessionStore.clear(ctx)
        SessionExpiryWorker.cancel(ctx)
        Notifier.cancel(ctx, Notifier.ID_SESSION)
        mapOf("result" to "relinquished", "reason" to "mode_changed_by_user")
      }
      is RestorePlan.Restore -> {
        val setResult = ringer.setMode(plan.target, trigger)
        when (val outcome = SessionLogic.onRestoreResult(plan.session, setResult)) {
          is RestoreOutcome.Ended -> {
            SessionStore.clear(ctx)
            SessionExpiryWorker.cancel(ctx)
            Notifier.cancel(ctx, Notifier.ID_SESSION)
            mapOf("result" to "restored", "mode" to plan.target.wire)
          }
          is RestoreOutcome.StillOwned -> {
            Notifier.restoreFailed(ctx, outcome.session, outcome.reason)
            mapOf("result" to "restore_failed", "reason" to outcome.reason)
          }
        }
      }
    }
    EventLog.log(ctx, "session_restore", mapOf("trigger" to trigger) + out)
    return out
  }

  /** Launch / boot / re-register: resolve PENDING, detect manual changes, and handle expiry (notify only). */
  fun recover(trigger: String): Map<String, Any?> = synchronized(StateLock) {
    val s = SessionStore.load(ctx)
    val out: Map<String, Any?> = when (val plan = SessionLogic.planRecover(s, ringer.current(), System.currentTimeMillis())) {
      RecoverPlan.Nothing -> mapOf("result" to "nothing", "ownership" to s?.ownership?.wire)
      is RecoverPlan.PromotePending -> {
        SessionStore.save(ctx, plan.session)
        SessionExpiryWorker.schedule(ctx, plan.session.maxUntil - System.currentTimeMillis())
        Notifier.sessionActive(ctx, plan.session)
        mapOf("result" to "promoted_pending")
      }
      is RecoverPlan.DiscardPending -> {
        SessionStore.clear(ctx)
        mapOf("result" to "discarded_pending")
      }
      is RecoverPlan.Relinquish -> {
        SessionStore.clear(ctx)
        SessionExpiryWorker.cancel(ctx)
        Notifier.cancel(ctx, Notifier.ID_SESSION)
        mapOf("result" to "relinquished")
      }
      is RecoverPlan.NotifyExpired -> {
        SessionStore.save(ctx, plan.session)
        Notifier.sessionExpired(ctx, plan.session)
        mapOf("result" to "expired_notified")
      }
      is RecoverPlan.Clear -> {
        SessionStore.clear(ctx)
        mapOf("result" to "cleared", "ownership" to plan.session.ownership.wire)
      }
    }
    EventLog.log(ctx, "session_recover", mapOf("trigger" to trigger) + out)
    out
  }

  fun sessionMap(): Map<String, Any?>? = current()?.let { SessionStore.toMap(it) + ("ownershipValid" to (it.ownership == Ownership.OWNED)) }
}

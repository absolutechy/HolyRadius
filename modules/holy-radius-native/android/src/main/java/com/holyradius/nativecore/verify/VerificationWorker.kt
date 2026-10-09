package com.holyradius.nativecore.verify

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.holyradius.nativecore.diag.EventLog
import com.holyradius.nativecore.domain.Confidence
import com.holyradius.nativecore.domain.EvidenceSource
import com.holyradius.nativecore.domain.Fix
import com.holyradius.nativecore.domain.FixCheck
import com.holyradius.nativecore.domain.FixValidator
import com.holyradius.nativecore.domain.PrototypeDecision
import com.holyradius.nativecore.geofence.GeofenceReceiver
import com.holyradius.nativecore.geofence.GeofenceRegistry
import com.holyradius.nativecore.notify.Notifier
import com.holyradius.nativecore.probe.CapabilityProbe
import com.holyradius.nativecore.session.SessionCoordinator
import com.holyradius.nativecore.store.ConfigStore
import com.holyradius.nativecore.store.GateStore
import com.holyradius.nativecore.store.StateLock
import com.holyradius.nativecore.util.awaitResult
import kotlinx.coroutines.withTimeoutOrNull

/**
 * M6: bounded background verification after DWELL. Requests at most [verifyMaxAttempts] fixes, each
 * with a hard timeout, validates age/accuracy, and decides with the Phase 1 prototype rule.
 * Expedited when quota allows; below API 31 WorkManager runs it as a short foreground service.
 */
class VerificationWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

  override suspend fun doWork(): Result {
    val ctx = applicationContext
    val fenceId = inputData.getString(KEY_FENCE_ID) ?: return Result.failure()
    val trigger = inputData.getString(KEY_TRIGGER) ?: "unknown"
    val enqueuedAt = inputData.getLong(KEY_ENQUEUED_AT, 0L)
    val startedAt = System.currentTimeMillis()
    val cfg = ConfigStore.load(ctx)
    val fence = GeofenceRegistry.find(ctx, fenceId)

    EventLog.log(
      ctx,
      "verify_start",
      mapOf(
        "fenceId" to fenceId,
        "trigger" to trigger,
        "startDelayMs" to (startedAt - enqueuedAt),
        "runAttempt" to runAttemptCount,
        "requestedExpedited" to true,
        "apiLevel" to Build.VERSION.SDK_INT
      )
    )
    if (fence == null) {
      EventLog.log(ctx, "verify_abort", mapOf("fenceId" to fenceId, "reason" to "fence_not_in_registry"))
      return Result.success()
    }

    var best: Fix? = null
    if (CapabilityProbe.hasFineLocation(ctx)) {
      for (attempt in 1..cfg.verifyMaxAttempts) {
        val attemptStart = System.currentTimeMillis()
        val outcome = requestFix(ctx, cfg.verifyTimeoutMs)
        val check = FixValidator.check(outcome.fix, cfg)
        EventLog.log(
          ctx,
          "verify_attempt",
          mapOf(
            "fenceId" to fenceId,
            "attempt" to attempt,
            "durationMs" to (System.currentTimeMillis() - attemptStart),
            "outcome" to outcome.reason,
            "check" to check.reason,
            "fix" to outcome.fix?.let { GeofenceReceiver.fixMap(it) }
          )
        )
        if (check is FixCheck.Valid) {
          val f = check.fix
          if (best == null || (f.accuracyM ?: Double.MAX_VALUE) < (best.accuracyM ?: Double.MAX_VALUE)) best = f
          if ((f.accuracyM ?: Double.MAX_VALUE) <= cfg.strongAccuracyM) break
        }
      }
    } else {
      EventLog.log(ctx, "verify_no_permission", mapOf("fenceId" to fenceId))
    }

    // Fall back to the ENTER triggering location; PrototypeDecision caps that source at UNCERTAIN.
    val (fix, source) = if (best != null) {
      best to EvidenceSource.VERIFIED_FIX
    } else {
      val evidence = synchronized(StateLock) { GateStore.getEvidence(ctx, fenceId) }
      evidence to EvidenceSource.TRIGGERING_LOCATION
    }
    val decision = PrototypeDecision.decide(fix, source, fence.lat, fence.lng, fence.verifyRadiusM, cfg)
    EventLog.log(
      ctx,
      "verify_decision",
      mapOf(
        "fenceId" to fenceId,
        "confidence" to decision.confidence.name,
        "reason" to decision.reason,
        "source" to source.wire,
        "distanceM" to decision.distanceM,
        "accuracyM" to decision.accuracyM,
        "totalMs" to (System.currentTimeMillis() - startedAt),
        "stopped" to isStopped
      )
    )

    when (decision.confidence) {
      Confidence.STRONG -> SessionCoordinator(ctx).apply(fenceId, "verify_strong")
      Confidence.UNCERTAIN -> Notifier.promptVibrate(ctx, fenceId, decision.reason)
      Confidence.INSUFFICIENT -> Unit
    }
    return Result.success()
  }

  private data class FixOutcome(val fix: Fix?, val reason: String)

  @SuppressLint("MissingPermission") // checked by the caller
  private suspend fun requestFix(ctx: Context, timeoutMs: Long): FixOutcome {
    val cts = CancellationTokenSource()
    return try {
      val loc = withTimeoutOrNull(timeoutMs) {
        LocationServices.getFusedLocationProviderClient(ctx)
          .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
          .awaitResult()
      }
      when {
        loc != null -> FixOutcome(GeofenceReceiver.toFix(loc), "fix")
        else -> FixOutcome(null, "timeout_or_null")
      }
    } catch (e: SecurityException) {
      FixOutcome(null, "security_exception")
    } catch (e: Exception) {
      FixOutcome(null, "${e.javaClass.simpleName}: ${e.message}")
    } finally {
      cts.cancel()
    }
  }

  /** Only used below API 31, where expedited work runs as a foreground service. No location FGS type (see A2). */
  override suspend fun getForegroundInfo(): ForegroundInfo {
    EventLog.log(applicationContext, "verify_foreground_info", mapOf("apiLevel" to Build.VERSION.SDK_INT))
    return ForegroundInfo(Notifier.ID_CHECKING, Notifier.checkingNotification(applicationContext))
  }

  companion object {
    private const val KEY_FENCE_ID = "fenceId"
    private const val KEY_TRIGGER = "trigger"
    private const val KEY_ENQUEUED_AT = "enqueuedAt"

    fun enqueue(ctx: Context, fenceId: String, trigger: String) {
      val request = OneTimeWorkRequestBuilder<VerificationWorker>()
        .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        .setInputData(
          workDataOf(KEY_FENCE_ID to fenceId, KEY_TRIGGER to trigger, KEY_ENQUEUED_AT to System.currentTimeMillis())
        )
        .addTag("holyradius-verify")
        .build()
      WorkManager.getInstance(ctx).enqueueUniqueWork("verify:$fenceId", ExistingWorkPolicy.KEEP, request)
      EventLog.log(ctx, "verify_enqueued", mapOf("fenceId" to fenceId, "trigger" to trigger))
    }
  }
}

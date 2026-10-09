package com.holyradius.nativecore.recovery

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.holyradius.nativecore.diag.EventLog
import com.holyradius.nativecore.geofence.GeofenceRegistrar
import com.holyradius.nativecore.geofence.GeofenceRegistry
import com.holyradius.nativecore.geofence.RegisterResult
import com.holyradius.nativecore.session.SessionCoordinator
import com.holyradius.nativecore.store.ConfigStore
import java.util.concurrent.TimeUnit

/**
 * M7: single unique worker for every re-registration trigger. Rechecks permissions and location
 * (inside GeofenceRegistrar), retries transient failures with exponential backoff, max 5 attempts.
 */
class ReRegisterWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
  override suspend fun doWork(): Result {
    val ctx = applicationContext
    val reason = inputData.getString(KEY_REASON) ?: "unknown"
    val reg = GeofenceRegistry.load(ctx)
    EventLog.log(ctx, "reregister_start", mapOf("reason" to reason, "attempt" to runAttemptCount, "fences" to reg.fences.size))
    if (reg.fences.isEmpty()) return Result.success()

    return when (val r = GeofenceRegistrar.register(ctx, reg.fences, ConfigStore.load(ctx), "reregister:$reason")) {
      is RegisterResult.Registered -> {
        SessionCoordinator(ctx).recover("reregister")
        Result.success()
      }
      // Precondition missing (permissions / location off): retrying won't help; the next trigger will.
      is RegisterResult.Skipped -> Result.success()
      is RegisterResult.Failed ->
        if (r.transient && runAttemptCount < MAX_ATTEMPTS - 1) Result.retry() else Result.failure()
    }
  }

  companion object {
    private const val KEY_REASON = "reason"
    private const val MAX_ATTEMPTS = 5

    fun enqueue(ctx: Context, reason: String) {
      val request = OneTimeWorkRequestBuilder<ReRegisterWorker>()
        .setInputData(workDataOf(KEY_REASON to reason))
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .addTag("holyradius-reregister")
        .build()
      WorkManager.getInstance(ctx).enqueueUniqueWork("holyradius-reregister", ExistingWorkPolicy.KEEP, request)
      EventLog.log(ctx, "reregister_enqueued", mapOf("reason" to reason))
    }
  }
}

package com.holyradius.nativecore.recovery

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.holyradius.nativecore.session.SessionCoordinator
import java.util.concurrent.TimeUnit

/**
 * One-shot timer scheduled when a session becomes OWNED. At max duration it runs recover(), which
 * NOTIFIES the user (never restores blindly). No polling and no location access.
 */
class SessionExpiryWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
  override suspend fun doWork(): Result {
    SessionCoordinator(applicationContext).recover("expiry_timer")
    return Result.success()
  }

  companion object {
    private const val UNIQUE = "holyradius-session-expiry"

    fun schedule(ctx: Context, delayMs: Long) {
      val request = OneTimeWorkRequestBuilder<SessionExpiryWorker>()
        .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
        .build()
      WorkManager.getInstance(ctx).enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(ctx: Context) {
      WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE)
    }
  }
}

package com.holyradius.nativecore.notify

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.holyradius.nativecore.diag.EventLog
import com.holyradius.nativecore.domain.Session

/** Notifications for prompts, active sessions and the short WorkManager foreground service. */
object Notifier {
  const val CH_PROMPTS = "holyradius_prompts"
  const val CH_STATUS = "holyradius_status"
  const val ID_CHECKING = 4301
  const val ID_SESSION = 4302
  const val ID_PROMPT = 4303

  // Prototype icon; replaced by an app-specific monochrome icon in Phase 6.
  private val SMALL_ICON = android.R.drawable.ic_lock_silent_mode

  fun ensureChannels(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val nm = ctx.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
      NotificationChannel(CH_PROMPTS, "Mosque prompts", NotificationManager.IMPORTANCE_HIGH).apply {
        description = "Asks before switching to Vibrate when presence is uncertain"
      }
    )
    nm.createNotificationChannel(
      NotificationChannel(CH_STATUS, "Status", NotificationManager.IMPORTANCE_LOW).apply {
        description = "Shows when HolyRadius has switched your phone to Vibrate"
      }
    )
  }

  fun checkingNotification(ctx: Context): Notification {
    ensureChannels(ctx)
    return NotificationCompat.Builder(ctx, CH_STATUS)
      .setSmallIcon(SMALL_ICON)
      .setContentTitle("HolyRadius")
      .setContentText("Checking location…")
      .setPriority(NotificationCompat.PRIORITY_LOW)
      .setOngoing(true)
      .build()
  }

  fun promptVibrate(ctx: Context, fenceId: String, reason: String) {
    post(
      ctx, ID_PROMPT, "prompt_vibrate", mapOf("fenceId" to fenceId, "reason" to reason),
      base(ctx, CH_PROMPTS)
        .setContentTitle("Likely at a mosque")
        .setContentText("Tap “Vibrate now” to switch your ringer to Vibrate.")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .addAction(0, "Vibrate now", action(ctx, NotificationActionReceiver.ACTION_VIBRATE_NOW, fenceId))
    )
  }

  fun manualActionNeeded(ctx: Context, fenceId: String, reason: String) {
    post(
      ctx, ID_PROMPT, "manual_action_needed", mapOf("fenceId" to fenceId, "reason" to reason),
      base(ctx, CH_PROMPTS)
        .setContentTitle("Please switch to Vibrate")
        .setContentText("HolyRadius couldn’t change the ringer automatically ($reason).")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .addAction(0, "Sound settings", settings(ctx))
    )
  }

  fun sessionActive(ctx: Context, session: Session) {
    post(
      ctx, ID_SESSION, "session_active", mapOf("sessionId" to session.sessionId),
      base(ctx, CH_STATUS)
        .setContentTitle("Phone on Vibrate")
        .setContentText("HolyRadius will restore your previous sound mode when you leave.")
        .setOngoing(true)
        .addAction(0, "Restore sound", action(ctx, NotificationActionReceiver.ACTION_RESTORE, session.fenceId))
    )
  }

  fun sessionExpired(ctx: Context, session: Session) {
    post(
      ctx, ID_SESSION, "session_expired", mapOf("sessionId" to session.sessionId),
      base(ctx, CH_PROMPTS)
        .setContentTitle("Still on Vibrate")
        .setContentText("HolyRadius isn’t sure you’ve left. Restore sound now?")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .addAction(0, "Restore sound", action(ctx, NotificationActionReceiver.ACTION_RESTORE, session.fenceId))
    )
  }

  fun restoreFailed(ctx: Context, session: Session, reason: String) {
    post(
      ctx, ID_SESSION, "restore_failed", mapOf("sessionId" to session.sessionId, "reason" to reason),
      base(ctx, CH_PROMPTS)
        .setContentTitle("Couldn’t restore sound")
        .setContentText("Please restore your ringer manually ($reason).")
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setAutoCancel(true)
        .addAction(0, "Sound settings", settings(ctx))
    )
  }

  fun cancel(ctx: Context, id: Int) = NotificationManagerCompat.from(ctx).cancel(id)

  private fun base(ctx: Context, channel: String): NotificationCompat.Builder {
    ensureChannels(ctx)
    val b = NotificationCompat.Builder(ctx, channel).setSmallIcon(SMALL_ICON)
    ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)?.let { launch ->
      b.setContentIntent(PendingIntent.getActivity(ctx, 0, launch, PendingIntent.FLAG_IMMUTABLE))
    }
    return b
  }

  private fun action(ctx: Context, action: String, fenceId: String): PendingIntent {
    val intent = Intent(ctx, NotificationActionReceiver::class.java)
      .setAction(action)
      .putExtra(NotificationActionReceiver.EXTRA_FENCE_ID, fenceId)
    return PendingIntent.getBroadcast(
      ctx, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )
  }

  private fun settings(ctx: Context): PendingIntent = PendingIntent.getActivity(
    ctx, 1, Intent(Settings.ACTION_SOUND_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE
  )

  /** Posts if allowed; every attempt (including suppressed ones) is logged for the reliability data. */
  private fun post(ctx: Context, id: Int, kind: String, info: Map<String, Any?>, builder: NotificationCompat.Builder) {
    val nm = NotificationManagerCompat.from(ctx)
    val posted = try {
      if (nm.areNotificationsEnabled()) {
        nm.notify(id, builder.build())
        true
      } else {
        false
      }
    } catch (e: SecurityException) {
      false
    }
    EventLog.log(ctx, "notification", info + mapOf("kind" to kind, "posted" to posted))
  }
}

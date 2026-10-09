package com.holyradius.nativecore.notify

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.holyradius.nativecore.diag.EventLog
import com.holyradius.nativecore.session.SessionCoordinator

/** Handles the explicit user actions on HolyRadius notifications. */
class NotificationActionReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    val ctx = context.applicationContext
    val fenceId = intent.getStringExtra(EXTRA_FENCE_ID) ?: "manual"
    Thread {
      try {
        EventLog.log(ctx, "notification_action", mapOf("action" to intent.action, "fenceId" to fenceId))
        when (intent.action) {
          ACTION_VIBRATE_NOW -> {
            Notifier.cancel(ctx, Notifier.ID_PROMPT)
            SessionCoordinator(ctx).apply(fenceId, "user_action")
          }
          ACTION_RESTORE -> SessionCoordinator(ctx).manualRestore("user_action")
        }
      } catch (t: Throwable) {
        EventLog.log(ctx, "notification_action_error", mapOf("error" to "${t.javaClass.simpleName}: ${t.message}"))
      } finally {
        pending.finish()
      }
    }.start()
  }

  companion object {
    const val ACTION_VIBRATE_NOW = "com.holyradius.nativecore.ACTION_VIBRATE_NOW"
    const val ACTION_RESTORE = "com.holyradius.nativecore.ACTION_RESTORE"
    const val EXTRA_FENCE_ID = "fenceId"
  }
}

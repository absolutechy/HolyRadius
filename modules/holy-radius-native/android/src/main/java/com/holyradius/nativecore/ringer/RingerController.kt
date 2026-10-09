package com.holyradius.nativecore.ringer

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.holyradius.nativecore.diag.EventLog
import com.holyradius.nativecore.domain.RingerMode
import com.holyradius.nativecore.domain.RingerSetResult

/** M3: set-and-verify ringer control. Every change logs before/after snapshots of all streams and DND. */
class RingerController(private val ctx: Context) {
  private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
  private val notifications = ctx.getSystemService(NotificationManager::class.java)

  fun current(): RingerMode = fromAndroid(audio.ringerMode)

  fun hasPolicyAccess(): Boolean = notifications.isNotificationPolicyAccessGranted

  fun setMode(target: RingerMode, trigger: String): RingerSetResult {
    val before = snapshot()
    val result = try {
      audio.ringerMode = toAndroid(target)
      var observed = current()
      // Some OEMs apply the change asynchronously; re-read briefly before declaring a mismatch.
      var tries = 0
      while (observed != target && tries < 3) {
        Thread.sleep(150)
        observed = current()
        tries++
      }
      if (observed == target) RingerSetResult.Ok(observed) else RingerSetResult.Mismatch(target, observed)
    } catch (e: SecurityException) {
      RingerSetResult.NeedsPolicyAccess
    } catch (e: Exception) {
      RingerSetResult.Failed("${e.javaClass.simpleName}: ${e.message}")
    }
    EventLog.log(
      ctx,
      "ringer_set",
      mapOf("requested" to target.wire, "trigger" to trigger, "result" to toMap(result), "before" to before, "after" to snapshot())
    )
    return result
  }

  /** Optional DND path; only works after the user granted Notification Policy Access. */
  fun setInterruptionFilter(filter: String): Map<String, Any?> {
    if (!hasPolicyAccess()) return mapOf("result" to "needs_policy_access")
    val value = when (filter) {
      "all" -> NotificationManager.INTERRUPTION_FILTER_ALL
      "priority" -> NotificationManager.INTERRUPTION_FILTER_PRIORITY
      "alarms" -> NotificationManager.INTERRUPTION_FILTER_ALARMS
      "none" -> NotificationManager.INTERRUPTION_FILTER_NONE
      else -> return mapOf("result" to "failed", "reason" to "unknown_filter")
    }
    val before = snapshot()
    val out = try {
      notifications.setInterruptionFilter(value)
      mapOf("result" to "ok", "observed" to filterName(notifications.currentInterruptionFilter))
    } catch (e: Exception) {
      mapOf("result" to "failed", "reason" to "${e.javaClass.simpleName}: ${e.message}")
    }
    EventLog.log(ctx, "dnd_set", mapOf("requested" to filter, "result" to out, "before" to before, "after" to snapshot()))
    return out
  }

  fun snapshot(): Map<String, Any?> = mapOf(
    "ringerMode" to current().wire,
    "ring" to stream(AudioManager.STREAM_RING),
    "notification" to stream(AudioManager.STREAM_NOTIFICATION),
    "alarm" to stream(AudioManager.STREAM_ALARM),
    "music" to stream(AudioManager.STREAM_MUSIC),
    "interruptionFilter" to filterName(notifications.currentInterruptionFilter),
    "policyAccess" to hasPolicyAccess()
  )

  private fun stream(type: Int) = mapOf("vol" to audio.getStreamVolume(type), "max" to audio.getStreamMaxVolume(type))

  companion object {
    fun fromAndroid(mode: Int): RingerMode = when (mode) {
      AudioManager.RINGER_MODE_SILENT -> RingerMode.SILENT
      AudioManager.RINGER_MODE_VIBRATE -> RingerMode.VIBRATE
      else -> RingerMode.NORMAL
    }

    fun toAndroid(mode: RingerMode): Int = when (mode) {
      RingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
      RingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
      RingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
    }

    fun filterName(f: Int) = when (f) {
      NotificationManager.INTERRUPTION_FILTER_ALL -> "all"
      NotificationManager.INTERRUPTION_FILTER_PRIORITY -> "priority"
      NotificationManager.INTERRUPTION_FILTER_ALARMS -> "alarms"
      NotificationManager.INTERRUPTION_FILTER_NONE -> "none"
      else -> "unknown_$f"
    }

    fun toMap(r: RingerSetResult): Map<String, Any?> = when (r) {
      is RingerSetResult.Ok -> mapOf("result" to r.wire, "observed" to r.observed.wire)
      is RingerSetResult.Mismatch -> mapOf("result" to r.wire, "requested" to r.requested.wire, "observed" to r.observed.wire)
      is RingerSetResult.Failed -> mapOf("result" to r.wire, "reason" to r.reason)
      RingerSetResult.NeedsPolicyAccess -> mapOf("result" to r.wire)
    }
  }
}

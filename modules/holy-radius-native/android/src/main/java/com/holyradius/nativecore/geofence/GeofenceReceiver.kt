package com.holyradius.nativecore.geofence

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.Location
import android.os.SystemClock
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingEvent
import com.holyradius.nativecore.diag.EventLog
import com.holyradius.nativecore.domain.EventGate
import com.holyradius.nativecore.domain.Fix
import com.holyradius.nativecore.domain.RegistryStatus
import com.holyradius.nativecore.domain.Transition
import com.holyradius.nativecore.domain.VerifyGate
import com.holyradius.nativecore.recovery.ReRegisterWorker
import com.holyradius.nativecore.session.SessionCoordinator
import com.holyradius.nativecore.store.ConfigStore
import com.holyradius.nativecore.store.GateStore
import com.holyradius.nativecore.store.StateLock
import com.holyradius.nativecore.verify.VerificationWorker

/**
 * M5: geofence transitions. Only short local I/O happens here (goAsync); location work is handed to
 * VerificationWorker and the ringer is touched only for EXIT via SessionCoordinator.
 */
class GeofenceReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    val ctx = context.applicationContext
    Thread {
      try {
        handle(ctx, intent)
      } catch (t: Throwable) {
        EventLog.log(ctx, "geofence_receiver_error", mapOf("error" to "${t.javaClass.simpleName}: ${t.message}"))
      } finally {
        pending.finish()
      }
    }.start()
  }

  private fun handle(ctx: Context, intent: Intent) {
    val event = GeofencingEvent.fromIntent(intent)
    if (event == null) {
      EventLog.log(ctx, "geofence_event_null", mapOf("action" to intent.action))
      return
    }
    if (event.hasError()) {
      val code = event.errorCode
      EventLog.log(ctx, "geofence_event_error", mapOf("code" to code, "name" to GeofenceStatusCodes.getStatusCodeString(code)))
      if (code == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) {
        // Location was turned off / Play services reset: fences are gone. Re-register when possible.
        GeofenceRegistry.update(ctx) { it.copy(status = RegistryStatus.NOT_AVAILABLE, lastError = "event_error_$code") }
        ReRegisterWorker.enqueue(ctx, "event_error_$code")
      }
      return
    }

    val transition = when (event.geofenceTransition) {
      Geofence.GEOFENCE_TRANSITION_ENTER -> Transition.ENTER
      Geofence.GEOFENCE_TRANSITION_DWELL -> Transition.DWELL
      Geofence.GEOFENCE_TRANSITION_EXIT -> Transition.EXIT
      else -> {
        EventLog.log(ctx, "geofence_unknown_transition", mapOf("value" to event.geofenceTransition))
        return
      }
    }
    val ids = event.triggeringGeofences?.map { it.requestId } ?: emptyList()
    val fix = event.triggeringLocation?.let { toFix(it) }
    EventLog.log(
      ctx,
      "geofence_transition",
      mapOf("transition" to transition.wire, "fenceIds" to ids, "triggeringLocation" to fix?.let { fixMap(it) })
    )

    val now = System.currentTimeMillis()
    for (id in ids) {
      val accepted = synchronized(StateLock) { gate(ctx, id, transition, fix, now) }
      if (accepted && transition == Transition.EXIT) SessionCoordinator(ctx).onExit(id)
    }
  }

  /** Dedup + cooldown bookkeeping for one fence. Returns false when the event is a duplicate. Caller holds [StateLock]. */
  private fun gate(ctx: Context, id: String, transition: Transition, fix: Fix?, now: Long): Boolean {
    val cfg = ConfigStore.load(ctx)
    val (afterDedup, duplicate) = EventGate.onTransition(GateStore.load(ctx), id, transition, now, cfg)
    if (duplicate) {
      EventLog.log(ctx, "geofence_duplicate_dropped", mapOf("fenceId" to id, "transition" to transition.wire))
      return false
    }
    var state = afterDedup
    if (transition == Transition.ENTER && fix != null) GateStore.putEvidence(ctx, id, fix)

    val wantsVerify = transition == Transition.DWELL || (transition == Transition.ENTER && cfg.verifyOnEnter)
    var verify = false
    if (wantsVerify) {
      val (afterGate, gate) = EventGate.tryStartVerification(state, id, now, cfg)
      state = afterGate
      verify = gate is VerifyGate.Accept
      if (!verify) EventLog.log(ctx, "verification_gated", mapOf("fenceId" to id, "gate" to gate.wire))
    }
    GateStore.save(ctx, state)
    if (verify) VerificationWorker.enqueue(ctx, id, transition.wire)
    return true
  }

  companion object {
    fun toFix(loc: Location): Fix = Fix(
      lat = loc.latitude,
      lng = loc.longitude,
      accuracyM = if (loc.hasAccuracy()) loc.accuracy.toDouble() else null,
      ageMs = (SystemClock.elapsedRealtimeNanos() - loc.elapsedRealtimeNanos) / 1_000_000,
      speedMps = if (loc.hasSpeed()) loc.speed.toDouble() else null
    )

    fun fixMap(f: Fix): Map<String, Any?> =
      mapOf("lat" to f.lat, "lng" to f.lng, "accuracyM" to f.accuracyM, "ageMs" to f.ageMs, "speedMps" to f.speedMps)
  }
}

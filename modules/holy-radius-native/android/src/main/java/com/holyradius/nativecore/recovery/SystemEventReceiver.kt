package com.holyradius.nativecore.recovery

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.location.LocationManager
import com.holyradius.nativecore.diag.EventLog
import com.holyradius.nativecore.domain.RegistryStatus
import com.holyradius.nativecore.geofence.GeofenceRegistry
import com.holyradius.nativecore.probe.CapabilityProbe
import com.holyradius.nativecore.session.SessionCoordinator

/** M7: system events that clear or invalidate geofences. Heavy work is delegated to ReRegisterWorker. */
class SystemEventReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    val pending = goAsync()
    val ctx = context.applicationContext
    val action = intent.action
    Thread {
      try {
        val locationOn = CapabilityProbe.locationEnabled(ctx)
        EventLog.log(ctx, "system_event", mapOf("action" to action, "locationEnabled" to locationOn))
        when (action) {
          Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
            val name = action.orEmpty().substringAfterLast('.')
            SessionCoordinator(ctx).recover(name)
            ReRegisterWorker.enqueue(ctx, name)
          }
          LocationManager.PROVIDERS_CHANGED_ACTION -> {
            if (!locationOn) {
              GeofenceRegistry.update(ctx) { it.copy(status = RegistryStatus.NOT_AVAILABLE, lastError = "location_off") }
            } else if (GeofenceRegistry.load(ctx).status != RegistryStatus.REGISTERED) {
              ReRegisterWorker.enqueue(ctx, "location_on")
            }
          }
        }
      } catch (t: Throwable) {
        EventLog.log(ctx, "system_event_error", mapOf("action" to action, "error" to "${t.javaClass.simpleName}: ${t.message}"))
      } finally {
        pending.finish()
      }
    }.start()
  }
}

package com.holyradius.nativecore.geofence

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.location.Geofence
import com.google.android.gms.location.GeofenceStatusCodes
import com.google.android.gms.location.GeofencingRequest
import com.google.android.gms.location.LocationServices
import com.holyradius.nativecore.config.PrototypeConfig
import com.holyradius.nativecore.diag.EventLog
import com.holyradius.nativecore.domain.RegistryStatus
import com.holyradius.nativecore.probe.CapabilityProbe
import com.holyradius.nativecore.util.awaitResult

sealed interface RegisterResult {
  fun toMap(): Map<String, Any?>

  data class Registered(val count: Int) : RegisterResult {
    override fun toMap() = mapOf("result" to "registered", "count" to count)
  }

  data class Skipped(val reason: String) : RegisterResult {
    override fun toMap() = mapOf("result" to "skipped", "reason" to reason)
  }

  data class Failed(val code: Int?, val name: String) : RegisterResult {
    /** Worth retrying with backoff (not a permission/precondition problem). */
    val transient: Boolean get() = code != GeofenceStatusCodes.GEOFENCE_TOO_MANY_GEOFENCES &&
      code != GeofenceStatusCodes.GEOFENCE_TOO_MANY_PENDING_INTENTS

    override fun toMap() = mapOf("result" to "failed", "code" to code, "name" to name)
  }
}

/** M4: registers the expected fence set with Play-services geofencing. */
object GeofenceRegistrar {
  const val ACTION_GEOFENCE = "com.holyradius.nativecore.ACTION_GEOFENCE"
  private const val REQUEST_CODE = 4201

  fun pendingIntent(ctx: Context): PendingIntent {
    val intent = Intent(ctx, GeofenceReceiver::class.java).setAction(ACTION_GEOFENCE)
    // Play services fills in extras, so the PendingIntent must be mutable on API 31+.
    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
      (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
    return PendingIntent.getBroadcast(ctx, REQUEST_CODE, intent, flags)
  }

  @SuppressLint("MissingPermission") // checked by the precheck below
  suspend fun register(ctx: Context, fences: List<FenceSpec>, cfg: PrototypeConfig, trigger: String): RegisterResult {
    val dev = CapabilityProbe.deviceSnapshot(ctx)
    val result: RegisterResult = when {
      fences.isEmpty() -> {
        unregisterAll(ctx, "empty_set")
        RegisterResult.Skipped("no_fences")
      }
      !dev.hasRequiredPermissions -> RegisterResult.Skipped("permissions_missing")
      !dev.locationEnabled -> RegisterResult.Skipped("location_off")
      else -> try {
        val client = LocationServices.getGeofencingClient(ctx)
        val pi = pendingIntent(ctx)
        // Replace semantics: drop everything registered under our PendingIntent, then add the set.
        runCatching { client.removeGeofences(pi).awaitResult() }
        val request = GeofencingRequest.Builder()
          .setInitialTrigger(GeofencingRequest.INITIAL_TRIGGER_DWELL)
          .addGeofences(fences.map { build(it, cfg) })
          .build()
        client.addGeofences(request, pi).awaitResult()
        RegisterResult.Registered(fences.size)
      } catch (e: ApiException) {
        RegisterResult.Failed(e.statusCode, GeofenceStatusCodes.getStatusCodeString(e.statusCode))
      } catch (e: SecurityException) {
        RegisterResult.Skipped("security_exception")
      } catch (e: Exception) {
        RegisterResult.Failed(null, "${e.javaClass.simpleName}: ${e.message}")
      }
    }

    GeofenceRegistry.update(ctx) { reg ->
      reg.copy(
        fences = fences,
        status = when (result) {
          is RegisterResult.Registered -> RegistryStatus.REGISTERED
          is RegisterResult.Skipped -> if (fences.isEmpty()) RegistryStatus.NONE else RegistryStatus.SKIPPED_PRECHECK
          is RegisterResult.Failed ->
            if (result.code == GeofenceStatusCodes.GEOFENCE_NOT_AVAILABLE) RegistryStatus.NOT_AVAILABLE else RegistryStatus.FAILED
        },
        lastError = (result as? RegisterResult.Failed)?.name ?: (result as? RegisterResult.Skipped)?.reason,
        lastAttemptAt = System.currentTimeMillis(),
        bootCount = dev.bootCount,
        packageUpdateTime = dev.packageUpdateTime
      )
    }
    EventLog.log(ctx, "geofence_register", mapOf("trigger" to trigger, "count" to fences.size) + result.toMap())
    return result
  }

  suspend fun unregisterAll(ctx: Context, trigger: String) {
    val outcome = runCatching {
      LocationServices.getGeofencingClient(ctx).removeGeofences(pendingIntent(ctx)).awaitResult()
    }
    EventLog.log(
      ctx,
      "geofence_unregister_all",
      mapOf("trigger" to trigger, "ok" to outcome.isSuccess, "error" to outcome.exceptionOrNull()?.message)
    )
  }

  private fun build(f: FenceSpec, cfg: PrototypeConfig): Geofence = Geofence.Builder()
    .setRequestId(f.id)
    .setCircularRegion(f.lat, f.lng, f.radiusM.toFloat())
    .setExpirationDuration(Geofence.NEVER_EXPIRE)
    .setTransitionTypes(
      Geofence.GEOFENCE_TRANSITION_ENTER or Geofence.GEOFENCE_TRANSITION_DWELL or Geofence.GEOFENCE_TRANSITION_EXIT
    )
    .setLoiteringDelay(f.loiterMs.toInt())
    .setNotificationResponsiveness(cfg.responsivenessMs.toInt())
    .build()
}

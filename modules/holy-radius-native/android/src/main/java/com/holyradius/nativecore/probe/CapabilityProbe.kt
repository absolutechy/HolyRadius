package com.holyradius.nativecore.probe

import android.Manifest
import android.app.NotificationManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.app.NotificationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.holyradius.nativecore.domain.DeviceSnapshot

/** M2: read-only capability and permission probe. Never requests anything itself. */
object CapabilityProbe {
  private fun granted(ctx: Context, perm: String) =
    ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED

  fun hasFineLocation(ctx: Context) = granted(ctx, Manifest.permission.ACCESS_FINE_LOCATION)

  fun hasBackgroundLocation(ctx: Context) =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      granted(ctx, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    } else {
      hasFineLocation(ctx)
    }

  fun hasGeofencingPermissions(ctx: Context) = hasFineLocation(ctx) && hasBackgroundLocation(ctx)

  fun notificationsEnabled(ctx: Context) = NotificationManagerCompat.from(ctx).areNotificationsEnabled()

  fun hasPolicyAccess(ctx: Context) =
    ctx.getSystemService(NotificationManager::class.java).isNotificationPolicyAccessGranted

  fun locationEnabled(ctx: Context) =
    LocationManagerCompat.isLocationEnabled(ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager)

  fun bootCount(ctx: Context): Int? =
    Settings.Global.getInt(ctx.contentResolver, Settings.Global.BOOT_COUNT, -1).takeIf { it >= 0 }

  fun packageUpdateTime(ctx: Context): Long =
    ctx.packageManager.getPackageInfo(ctx.packageName, 0).lastUpdateTime

  fun deviceSnapshot(ctx: Context) = DeviceSnapshot(
    bootCount = bootCount(ctx),
    packageUpdateTime = packageUpdateTime(ctx),
    locationEnabled = locationEnabled(ctx),
    hasRequiredPermissions = hasGeofencingPermissions(ctx)
  )

  private fun standbyBucket(ctx: Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
    val bucket = ctx.getSystemService(UsageStatsManager::class.java).appStandbyBucket
    return when (bucket) {
      UsageStatsManager.STANDBY_BUCKET_ACTIVE -> "active"
      UsageStatsManager.STANDBY_BUCKET_WORKING_SET -> "working_set"
      UsageStatsManager.STANDBY_BUCKET_FREQUENT -> "frequent"
      UsageStatsManager.STANDBY_BUCKET_RARE -> "rare"
      45 -> "restricted" // STANDBY_BUCKET_RESTRICTED (API 30)
      else -> "bucket_$bucket"
    }
  }

  private fun playServices(ctx: Context): Map<String, Any?> {
    val code = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(ctx)
    val version = runCatching {
      val info = ctx.packageManager.getPackageInfo("com.google.android.gms", 0)
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
    }.getOrNull()
    return mapOf("available" to (code == ConnectionResult.SUCCESS), "statusCode" to code, "version" to version)
  }

  fun snapshot(ctx: Context): Map<String, Any?> {
    val pm = ctx.getSystemService(PowerManager::class.java)
    return mapOf(
      "apiLevel" to Build.VERSION.SDK_INT,
      "release" to Build.VERSION.RELEASE,
      "manufacturer" to Build.MANUFACTURER,
      "model" to Build.MODEL,
      "fineLocation" to hasFineLocation(ctx),
      "coarseLocation" to granted(ctx, Manifest.permission.ACCESS_COARSE_LOCATION),
      "backgroundLocation" to hasBackgroundLocation(ctx),
      "notificationsEnabled" to notificationsEnabled(ctx),
      "policyAccess" to hasPolicyAccess(ctx),
      "locationEnabled" to locationEnabled(ctx),
      "playServices" to playServices(ctx),
      "ignoringBatteryOptimizations" to pm.isIgnoringBatteryOptimizations(ctx.packageName),
      "powerSaveMode" to pm.isPowerSaveMode,
      "standbyBucket" to standbyBucket(ctx),
      "bootCount" to bootCount(ctx),
      "packageUpdateTime" to packageUpdateTime(ctx)
    )
  }
}

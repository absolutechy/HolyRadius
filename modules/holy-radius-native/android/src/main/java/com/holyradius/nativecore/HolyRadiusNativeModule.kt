package com.holyradius.nativecore

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.holyradius.nativecore.diag.EventLog
import com.holyradius.nativecore.domain.ReRegDecision
import com.holyradius.nativecore.domain.ReRegistrationPolicy
import com.holyradius.nativecore.domain.RingerMode
import com.holyradius.nativecore.geofence.FenceSpec
import com.holyradius.nativecore.geofence.GeofenceRegistrar
import com.holyradius.nativecore.geofence.GeofenceRegistry
import com.holyradius.nativecore.notify.Notifier
import com.holyradius.nativecore.probe.CapabilityProbe
import com.holyradius.nativecore.recovery.ReRegisterWorker
import com.holyradius.nativecore.ringer.RingerController
import com.holyradius.nativecore.session.SessionCoordinator
import com.holyradius.nativecore.store.ConfigStore
import com.holyradius.nativecore.verify.VerificationWorker
import expo.modules.kotlin.exception.Exceptions
import expo.modules.kotlin.functions.Coroutine
import expo.modules.kotlin.modules.Module
import expo.modules.kotlin.modules.ModuleDefinition
import org.json.JSONArray
import org.json.JSONObject

/**
 * M9: JS bridge. Complex values cross the bridge as JSON strings to keep the typed contract in
 * src/HolyRadiusNative.types.ts explicit. Background logic never depends on this module being alive.
 */
class HolyRadiusNativeModule : Module() {
  private val ctx: Context
    get() = appContext.reactContext?.applicationContext ?: throw Exceptions.ReactContextLost()

  override fun definition() = ModuleDefinition {
    Name("HolyRadiusNative")

    OnCreate {
      appContext.reactContext?.applicationContext?.let { Notifier.ensureChannels(it) }
    }

    // --- M0 / M1 / M2 ---
    Function("ping") {
      mapOf("pong" to true, "apiLevel" to Build.VERSION.SDK_INT)
    }

    AsyncFunction("probe") {
      val snapshot = CapabilityProbe.snapshot(ctx)
      EventLog.log(ctx, "probe", snapshot)
      snapshot
    }

    AsyncFunction("getLog") { sinceId: Double, limit: Int ->
      EventLog.read(ctx, sinceId.toLong(), limit)
    }

    AsyncFunction("clearLog") {
      EventLog.clear(ctx)
    }

    AsyncFunction("logEvent") { source: String, type: String, payloadJson: String ->
      EventLog.logJson(ctx, source, type, payloadJson)
    }

    AsyncFunction("exportLog") {
      val file = EventLog.exportFile(ctx)
      val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.holyradius.diag", file)
      val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      send.clipData = ClipData.newRawUri(file.name, uri)
      val chooser = Intent.createChooser(send, "Export HolyRadius log")
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
      ctx.startActivity(chooser)
      file.absolutePath
    }

    // --- M3 ringer ---
    AsyncFunction("ringerSnapshot") {
      RingerController(ctx).snapshot()
    }

    AsyncFunction("setRingerMode") { mode: String ->
      RingerController.toMap(RingerController(ctx).setMode(RingerMode.fromWire(mode), "diagnostics"))
    }

    AsyncFunction("setInterruptionFilter") { filter: String ->
      RingerController(ctx).setInterruptionFilter(filter)
    }

    // --- settings shortcuts (never forced; user-initiated only) ---
    AsyncFunction("openPolicyAccessSettings") {
      ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    AsyncFunction("openAppSettings") {
      ctx.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
          .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
      )
    }

    AsyncFunction("openLocationSettings") {
      ctx.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // --- M4 / M7 geofences ---
    AsyncFunction("registerFences") Coroutine { fencesJson: String ->
      val arr = JSONArray(fencesJson)
      val fences = List(arr.length()) { FenceSpec.fromJson(arr.getJSONObject(it)) }
      GeofenceRegistrar.register(ctx, fences, ConfigStore.load(ctx), "diagnostics").toMap()
    }

    AsyncFunction("unregisterAll") Coroutine { ->
      GeofenceRegistrar.unregisterAll(ctx, "diagnostics")
      GeofenceRegistry.update(ctx) { it.copy(fences = emptyList(), status = com.holyradius.nativecore.domain.RegistryStatus.NONE) }
      true
    }

    AsyncFunction("getRegistry") {
      GeofenceRegistry.load(ctx).toJson().toString()
    }

    AsyncFunction("forceReRegister") {
      ReRegisterWorker.enqueue(ctx, "manual")
    }

    AsyncFunction("reconcileOnLaunch") {
      val decision = ReRegistrationPolicy.decide(GeofenceRegistry.load(ctx).snapshot(), CapabilityProbe.deviceSnapshot(ctx))
      if (decision is ReRegDecision.Needed) ReRegisterWorker.enqueue(ctx, "launch:${decision.reason}")
      val kind = when (decision) {
        is ReRegDecision.Needed -> "needed"
        is ReRegDecision.NotNeeded -> "not_needed"
        is ReRegDecision.Blocked -> "blocked"
      }
      EventLog.log(ctx, "launch_reconcile", mapOf("decision" to kind, "reason" to decision.reason))
      val recover = SessionCoordinator(ctx).recover("launch")
      mapOf("decision" to kind, "reason" to decision.reason, "recover" to recover)
    }

    // --- M6 diagnostics: run verification now, bypassing the dedup/cooldown gate ---
    AsyncFunction("triggerVerification") { fenceId: String ->
      VerificationWorker.enqueue(ctx, fenceId, "manual")
    }

    // --- M8 session ---
    AsyncFunction("getSession") {
      SessionCoordinator(ctx).sessionMap()
    }

    AsyncFunction("manualVibrate") {
      SessionCoordinator(ctx).apply("manual", "diagnostics")
    }

    AsyncFunction("manualRestore") {
      SessionCoordinator(ctx).manualRestore("diagnostics")
    }

    AsyncFunction("recoverSession") {
      SessionCoordinator(ctx).recover("diagnostics")
    }

    // --- centralized config ---
    AsyncFunction("getConfig") {
      ConfigStore.toJson(ConfigStore.load(ctx)).toString()
    }

    AsyncFunction("setConfig") { configJson: String ->
      val merged = ConfigStore.toJson(ConfigStore.load(ctx))
      val patch = JSONObject(configJson)
      for (key in patch.keys()) merged.put(key, patch.get(key))
      val cfg = ConfigStore.fromJson(merged)
      ConfigStore.save(ctx, cfg)
      EventLog.log(ctx, "config_set", mapOf("patch" to patch))
      ConfigStore.toJson(cfg).toString()
    }

    AsyncFunction("resetConfig") {
      ConfigStore.reset(ctx)
      ConfigStore.toJson(ConfigStore.load(ctx)).toString()
    }
  }
}

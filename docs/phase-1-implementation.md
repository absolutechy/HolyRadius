# Phase 1 — Native Feasibility Prototype

> Reference doc for Phase 1 of HolyRadius. Architecture and assumptions are in
> [`decisions.md`](./decisions.md); the test protocol and results sheet are in
> [`phase-1-results.md`](./phase-1-results.md).

## Goal

Prove or disprove, **on real hardware and on an emulator**, that native Android can:

- **(a)** wake on geofence ENTER / DWELL / EXIT with the app swiped away;
- **(b)** obtain a bounded, validated location fix in the background;
- **(c)** set Vibrate, verify it, and restore it safely;
- **(d)** recover geofences after reboot, app update, location toggle and geofencing errors.

Then compare the same scenarios against an **Expo TaskManager baseline**.

Phase 1 has **no product UI**, only a diagnostics screen. It **does not** use OSM data. Test fences are placed manually, at the current location or at typed coordinates.

Gate **G1** (end of this doc) decides how Phase 2 is designed.

## Build targets

| Item | Value |
|---|---|
| Expo SDK | 57 (`expo ~57.0.x`, RN 0.86) |
| Dev client | `expo-dev-client` (Expo Go cannot load the native module) |
| applicationId | `com.holyradius.app` (change in `app.json` before first store upload) |
| minSdk / compileSdk / targetSdk | 24 / 36 / 36 (Expo SDK 57 defaults) |
| Native libs | `play-services-location 21.3.0`, `androidx.work:work-runtime-ktx 2.10.1` |
| Device build | `eas build --profile development --platform android` |
| Emulator build | `npx expo run:android` (Android Studio emulator, **Google Play** system image) |

## Source map

```
modules/holy-radius-native/                 local Expo module (autolinked from ./modules)
  expo-module.config.json
  index.ts                                  public TS API (re-exports)
  src/HolyRadiusNative.types.ts             typed bridge results
  src/HolyRadiusNativeModule.ts             requireNativeModule binding
  android/build.gradle
  android/src/main/AndroidManifest.xml      permissions, receivers, FileProvider
  android/src/main/res/xml/hr_diag_paths.xml
  android/src/main/java/com/holyradius/nativecore/
    HolyRadiusNativeModule.kt               M9  bridge (Expo Modules DSL)
    domain/                                 pure Kotlin, no Android imports (JUnit on JVM)
      Geo.kt                                haversine
      RingerMode.kt                         ringer enum + typed results
      FixValidator.kt                       M6  age/accuracy validation
      PrototypeDecision.kt                  M6  STRONG / UNCERTAIN / INSUFFICIENT (prototype rule)
      EventGate.kt                          M5  dedup, cooldown, hourly verification cap
      SessionLogic.kt                       M8  apply / restore / recover decisions
      ReRegistrationPolicy.kt               M7  when to re-register
      AtomicTextFile.kt                     M8  temp → fsync → rename writer
    config/PrototypeConfig.kt               centralized defaults (overridable from JS)
    diag/EventLog.kt                        M1
    probe/CapabilityProbe.kt                M2
    ringer/RingerController.kt              M3
    geofence/GeofenceRegistry.kt            M4  persisted expected set + last status
    geofence/GeofenceRegistrar.kt           M4
    geofence/GeofenceReceiver.kt            M5
    verify/VerificationWorker.kt            M6
    recovery/SystemEventReceiver.kt         M7
    recovery/ReRegisterWorker.kt            M7
    session/SessionStore.kt                 M8
    session/SessionCoordinator.kt           M8
    notify/Notifier.kt                      notifications + actions
    notify/NotificationActionReceiver.kt    "Vibrate now" / "Restore sound"
native-tests/                               JVM Gradle project running JUnit on domain/
src/app/                                    Expo Router screens (M9 diagnostics)
src/features/diagnostics/                   M9 UI pieces
src/features/taskmanager-baseline/          M10
src/config/                                 TS-side centralized config
```

`domain/` is compiled twice: once in the Android module, and once by `native-tests/` on a plain JVM. That keeps the decision logic testable without a device.

## Module breakdown

Each module lists its responsibility, key details, and **done-when**.

### P1-M0 Scaffold & build
- Expo TS + Expo Router (`src/app/`).
- Local Expo module `holy-radius-native` (Kotlin) with its own `AndroidManifest.xml`, merged automatically.
- `eas.json` profiles: `development` (dev client, internal), `preview` (internal APK).
- Gradle deps: `play-services-location`, `work-runtime-ktx`.
- **Done when:** the dev build installs on the emulator and a device, and JS `HolyRadiusNative.ping()` returns `"pong"` with the API level.

### P1-M1 Diagnostics EventLog
Everything depends on this module.
- Append-only JSONL in `filesDir/diag/events.jsonl`. When the file passes 512 KB it rotates to `events.1.jsonl` (one generation).
- Each entry: monotonic `id`, wall time `t`, `elapsedRealtime` `et`, `source` (`native` | `taskmanager` | `js`), `type`, `payload`, and process state (`fg` / `bg`, plus `coldStart` on the first entry of a process).
- Bridge: `getLog(sinceId, limit)`, `clearLog()`, `exportLog()` (share sheet via FileProvider), `logEvent(source, type, payload)` (used by JS and the TaskManager baseline).
- **Done when:** entries survive process death, and an exported file opens in any text viewer.

### P1-M2 Capability & permission probe
- **Reads:** fine/coarse/background location, POST_NOTIFICATIONS, Notification Policy Access, location enabled, Play-services availability, battery-optimization exemption (read-only), standby bucket, API level, manufacturer/model, boot count.
- **Requests**, in order:
  1. foreground location
  2. background location (separate step, Android 11+ goes to Settings)
  3. notifications (API 33+)
- DND access is **optional**: an explicit button opens `ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS`.
- **Done when:** the probe is logged on every launch and shown on the diagnostics screen.

### P1-M3 RingerController
- `getMode()`.
- `setMode(VIBRATE | NORMAL | SILENT)`: set, then read back, returning a typed result: `ok(observed)` | `needs_policy_access` | `mismatch(observed)` | `failed(reason)`.
- `snapshot()`: ringer mode; `STREAM_RING` / `NOTIFICATION` / `ALARM` / `MUSIC` volumes; `currentInterruptionFilter`; policy access.
- Optional `setDnd(filter)` works only when policy access is granted (opt-in DND path).
- Every set logs before/after snapshots, so alarm/media/DND side effects are measured, not assumed.
- **Done when:** for each test device we have recorded whether VIBRATE needs policy access, and the stream/DND effects.

### P1-M4 GeofenceRegistrar
- `register(fences)`. Each fence is `{id, lat, lng, radiusM, loiterMs, verifyRadiusM}`.
  - Transitions: ENTER | DWELL | EXIT.
  - `setInitialTrigger(INITIAL_TRIGGER_DWELL)`, to avoid a false ENTER when registering while inside.
  - `setNotificationResponsiveness(config.responsivenessMs)`.
  - `NEVER_EXPIRE`.
- PendingIntent → `GeofenceReceiver` with **FLAG_MUTABLE**; API 31+ requires it, because Play services fills in extras.
- The **registry** (expected fence set, last status, boot count, package update time) is persisted in `GeofenceRegistry`.
- `ApiException` codes map to typed errors: `1000 not_available`, `1001 too_many_geofences`, `1002 too_many_pending_intents`.
- Precheck before every registration: fine + background location granted, and location enabled. If the precheck fails, the call is skipped and logged with a reason.
- `unregisterAll()`.
- **Done when:** test fences register, survive an app swipe, and errors are logged with codes.

### P1-M5 GeofenceReceiver → event pipeline
- Parse `GeofencingEvent`.
  - **Error** → log it; for code 1000, mark the registry `not_available` and enqueue `ReRegisterWorker`.
  - **Otherwise** → log the transition, fence ids, the triggering location (accuracy, age, speed when present) and process state.
- `EventGate` (pure) **dedups** `(fenceId, transition)` within `dedupWindowMs`. It also applies a **per-fence verification cooldown** and a **global hourly verification cap**.
- **DWELL** (or ENTER when `verifyOnEnter=true`) → enqueue `VerificationWorker`, carrying the triggering location as evidence.
- **EXIT** → `SessionCoordinator.onExit()`.
- `goAsync()` covers only short local I/O. No network and no location requests run in the receiver.
- **Done when:** transitions are logged with the app swiped away, and latency can be measured against the mock-route timestamps.

### P1-M6 VerificationWorker (bounded background fix)
- Expedited `OneTimeWorkRequest` with `OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST`. Logs: enqueue→start delay, whether it was enqueued as expedited, and the stop reason if stopped.
- **API < 31:** WorkManager runs expedited work as a **short foreground service** (`SystemForegroundService`). `getForegroundInfo()` supplies a low-importance "Checking location…" notification **without** a location FGS type. Phase 1 records whether location access works there (the app holds background location). If not, the fallback is non-expedited work on API < 31.
- **API ≥ 31:** a JobScheduler expedited job with no FGS. Quotas apply; expedited work does **not** bypass background-location limits.
- Uses `getCurrentLocation(PRIORITY_HIGH_ACCURACY)` with a hard timeout (`verifyTimeoutMs`, default 20 s), at most `verifyMaxAttempts` (default 2) attempts. Cancelled on timeout.
- `FixValidator`: age ≤ `maxFixAgeMs` (default 30 s), accuracy present, accuracy ≤ `hardMaxAccuracyM` (default 120 m).
- `PrototypeDecision` (replaced by `PresenceEngine` in Phase 2):
  - **STRONG**: valid fix, distance ≤ `verifyRadiusM`, and accuracy ≤ `strongAccuracyM` (default 35 m).
  - **UNCERTAIN**: distance − accuracy ≤ `verifyRadiusM`.
  - **INSUFFICIENT**: anything else, or no valid fix.
  - **Triggering-location-only evidence is capped at UNCERTAIN.** It never auto-applies Vibrate.
- Outcomes:
  - STRONG → `SessionCoordinator.apply()`
  - UNCERTAIN → "Likely at a mosque — Vibrate now?" notification
  - INSUFFICIENT → log only
- **Done when:** success rate, latency, accuracy and expedited status are recorded per device / API level, including Doze and battery saver.

### P1-M7 Re-registration triggers
- `SystemEventReceiver` listens for:
  - `BOOT_COMPLETED`
  - `LOCKED_BOOT_COMPLETED` (logged only)
  - `MY_PACKAGE_REPLACED`
  - `PROVIDERS_CHANGED` (re-register only when location is on again)
- Geofence error 1000 from M5 also triggers re-registration.
- App launch: `ReRegistrationPolicy` (pure) compares the stored boot count, package update time and last status with current values. It re-registers only when they differ or the last status is not `registered`.
- Every trigger enqueues one unique `ReRegisterWorker` (`ExistingWorkPolicy.KEEP`). A pending worker reads the current state when it runs, so it also covers later triggers. The worker:
  1. rechecks permissions and location;
  2. registers;
  3. on a transient failure, returns `retry()` with exponential backoff (30 s base), at most 5 attempts.
- After a successful re-registration it also runs `SessionCoordinator.recover()`.
- **Done when:** fences are restored and logged after reboot, `adb install -r`, location off→on, and a Play-services data clear.

### P1-M8 Prototype ringer session
The full state machine comes in Phase 3.
- `SessionStore` is a single JSON record written via `AtomicTextFile` (temp file → `fsync` → rename): `{sessionId, fenceId, previousMode, appliedMode, startedAt, maxUntil, ownership}`.
- **Apply:**
  - If the current mode already equals the target (e.g. the user was already on Vibrate), **no session is created**, so there is nothing to restore later.
  - Otherwise write `PENDING` → `setMode(VIBRATE)` → read back → `OWNED`.
  - If the result is `needs_policy_access` / `mismatch` / `failed`, delete the session and show a manual-action notification.
- **Restore** (EXIT, or the manual "Restore sound" action):
  - Requires an `OWNED` session **and** current mode == applied mode.
  - If the current mode differs, the session becomes `RELINQUISHED` and the ringer is not touched.
  - **No session means no restore, ever.**
- **Recover** (launch / boot / re-register):
  - `PENDING` + current == applied → `OWNED`.
  - `PENDING` + current ≠ applied → discard.
  - `OWNED` past `maxUntil` → **notify** "Still on Vibrate — restore?". Sound is never restored blindly.
- **Done when:**
  - A manual ringer change while inside → no restore.
  - The process is killed between steps → state is consistent after relaunch.
  - Reboot while inside → the session survives and is handled.

### P1-M9 Bridge + diagnostics screen
A single screen with no styling effort.
- Sections:
  - **Probe / Permissions**: refresh probe, request permissions step by step, open DND settings (optional), open app settings.
  - **Ringer**: Snapshot, Set Vibrate, Set Normal, Set Silent (shows the typed result).
  - **Fences**: register a test fence at the current location or at typed lat/lng, with radius / loiter / verify radius; list the registry; unregister all; force re-register.
  - **Session**: show the current session; manual restore; run recover.
  - **Baseline**: enable/disable the TaskManager baseline.
  - **Log**: live view (polls `getLog(sinceId)` every 2 s while visible), source filter, clear, export.
- **Done when:** every module's action can be triggered and observed without adb.

### P1-M10 Expo TaskManager baseline
- `expo-location` `startGeofencingAsync` + `TaskManager.defineTask`, defined at module scope in the root entry so headless starts can find it. It logs to the same EventLog with `source=taskmanager` via `logEvent`.
- Uses the same coordinates under separate ids (`tm:<id>`). Both pipelines share the 100-fence per-app limit, which is fine here.
- Expo geofencing supports only ENTER/EXIT (no DWELL, no loitering delay; confirmed in `expo-location` 57 source). That gap is part of the comparison.
- **Done when:** both pipelines have logged the same scenarios, producing comparable rows.

### P1-M11 Test protocol & results
The protocol and results template live in [`phase-1-results.md`](./phase-1-results.md).
- **Done when:** results are filled in for at least one emulator configuration and one physical device.

## Detailed runtime flow

```
App launch ─► EventLog(coldStart) ─► Probe (M2) ─► log
          └─► ReRegistrationPolicy (M7): stale? ─► ReRegisterWorker ─► precheck ─► register (M4)
          └─► SessionCoordinator.recover() (M8)

Play services ──transition──► GeofenceReceiver (M5)
  │ error (1000…) ─► registry.status = not_available ─► ReRegisterWorker (backoff)
  │ ENTER  ─► log + remember triggering location (evidence only; never acts alone)
  │ DWELL  ─► EventGate: dedup / cooldown / hourly cap
  │            └► enqueue VerificationWorker (expedited → non-expedited on quota)
  │                 └► getCurrentLocation(timeout 20 s, ≤ 2 attempts)
  │                      ├ STRONG       ─► SessionCoordinator.apply()
  │                      │                  current == VIBRATE? ─► no session (nothing to restore)
  │                      │                  PENDING ─► setMode(VIBRATE) ─► read back ─► OWNED
  │                      │                  needs_policy_access / mismatch / failed ─► notification
  │                      ├ UNCERTAIN    ─► notification "Vibrate now?" (user action applies)
  │                      └ INSUFFICIENT ─► log only
  │ EXIT   ─► SessionCoordinator.onExit()
  │            OWNED && current == applied ─► setMode(previous) ─► verify ─► ENDED
  │            current ≠ applied           ─► RELINQUISHED (ringer untouched)
  │            no session                  ─► nothing

BOOT_COMPLETED / MY_PACKAGE_REPLACED / PROVIDERS_CHANGED(on)
  ─► ReRegisterWorker ─► precheck ─► register (M4) ─► SessionCoordinator.recover()
```

## Implementation order (one commit per step)

1. M0 → M1 → M2: the foundation, visible on the diagnostics screen early.
2. M3: the ringer, testable immediately on a device.
3. M4 → M5 → M7.
4. M6.
5. M8.
6. M9 grows alongside steps 1–5.
7. M10 → M11.

## Centralized configuration (prototype defaults)

All values live in `PrototypeConfig.kt` (native) and `src/config/prototype.ts` (TS mirror). They can be overridden at runtime from the diagnostics screen. These defaults are **starting values to measure, not tuned values.**

| Key | Default | Notes |
|---|---|---|
| `defaultRadiusM` | 150 | coarse wake-up radius; also test 100 / 250 |
| `defaultLoiterMs` | 90 000 | DWELL loitering delay |
| `responsivenessMs` | 30 000 | geofence notification responsiveness |
| `defaultVerifyRadiusM` | 30 | prototype "inside" radius around the test point |
| `verifyOnEnter` | false | if true, ENTER also triggers verification |
| `verifyTimeoutMs` | 20 000 | per attempt |
| `verifyMaxAttempts` | 2 | |
| `maxFixAgeMs` | 30 000 | |
| `strongAccuracyM` | 35 | prototype STRONG threshold only |
| `hardMaxAccuracyM` | 120 | above this a fix is INSUFFICIENT |
| `dedupWindowMs` | 60 000 | per (fence, transition) |
| `verifyCooldownMs` | 600 000 | per fence |
| `maxVerificationsPerHour` | 6 | global |
| `maxSessionMs` | 10 800 000 | 3 h; on expiry → notify, never blind restore |

## What can be verified where

| Check | Cloud session (Claude) | You (EAS / Android Studio) |
|---|---|---|
| `npx tsc --noEmit`, `npx jest` | ✅ | ✅ |
| JUnit on pure `domain/` (JVM) | ✅ | ✅ `cd native-tests && gradle test` |
| `npx expo prebuild --platform android` | ✅ (config/manifest generation) | ✅ |
| Kotlin compile of Android code | ❌ (Google Maven / Android SDK blocked by network policy) | ✅ first real compile happens in your build |
| Device/emulator behavior | ❌ | ✅ M11 protocol |

## Gate G1 (decide before Phase 2)

- [ ] Native DWELL delivered with the app swiped away, on each tested device (y/n per device).
- [ ] Verification yields a valid fix within the timeout for a meaningful share of DWELL events. The threshold is set from the data.
- [ ] VIBRATE without policy access works / fails, per device (documented).
- [ ] Alarm / media / notification / DND side effects documented per device.
- [ ] Re-registration works after reboot, update, and location toggle.
- [ ] Native vs TaskManager comparison table → confirm Kotlin orchestration, or simplify.
- [ ] If background verification is unreliable, Phase 2 requires DWELL **and** a validated fix for automatic Vibrate. Everything else is notification-only.

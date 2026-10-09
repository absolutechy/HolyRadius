# Phase 1 — Test Protocol & Results

Fill in one results block per **device/emulator configuration**. Attach the exported diagnostics log (`Export log` on the diagnostics screen) for every run.

## 0. Setup

### Emulator (Android Studio)
1. Device Manager → create a Pixel device with a **Google Play** system image. A plain "Google APIs" image without Play Store may lack up-to-date Play services; Play is required for geofencing. Create images for API **30**, **33** and **35/36**.
2. Build and install: `npx expo run:android`. Or install the EAS dev-client APK and run `npx expo start --dev-client`.
3. In the emulator: Settings → Location → **On**, and Google Location Accuracy → On.
4. Mock movement: Extended Controls (⋯) → **Location** → **Routes** (or import a GPX/KML file), playback speed 1×. Single point: `adb emu geo fix <lng> <lat>` (note the order: longitude first).

### Physical device
1. Build: `eas build --profile development --platform android`, install the APK, then `npx expo start --dev-client`.
2. Disable developer "mock location" apps, and keep battery saver **off** unless the scenario says otherwise.

### Useful adb commands
| Purpose | Command |
|---|---|
| Force Doze | `adb shell dumpsys deviceidle force-idle` (undo: `adb shell dumpsys deviceidle unforce`) |
| Standby bucket | `adb shell am set-standby-bucket com.holyradius.app rare` (check: `get-standby-bucket`) |
| Battery saver | `adb shell settings put global low_power 1` (undo `0`) |
| Swipe-away equivalent | Recents → swipe the app away (NOT force stop) |
| Force stop (expected to break, documented) | `adb shell am force-stop com.holyradius.app` |
| Simulate app update | `adb install -r <apk>` |
| Reboot | `adb reboot` |
| Location off/on | `adb shell settings put secure location_mode 0` / `3` |
| Ringer mode now | `adb shell cmd audio get-ringer-mode-internal` (if available) or the diagnostics screen |
| Logcat for the module | `adb logcat -s HolyRadius` |

## 1. Scenario list

Run every scenario first with the app **open**, then with the app **swiped away**.

| ID | Scenario | How | Expected (prototype) |
|---|---|---|---|
| S1 | Walk-in + dwell | Route: 400 m away → fence centre at walking speed (≈1.4 m/s) → stay 5 min | ENTER → DWELL (≈ loiter delay) → verification → STRONG → Vibrate (session OWNED) |
| S2 | Walk-out | Continue S1: walk 400 m away | EXIT → restore to previous mode → ENDED |
| S3 | Drive-by | Route through the fence at ≈ 14 m/s without stopping | ENTER (maybe) + EXIT, **no DWELL, no verification, no ringer change** |
| S4 | Already on Vibrate | Set Vibrate manually first, then S1 + S2 | No session created; ringer untouched on exit |
| S5 | Manual change inside | After S1 OWNED, set Normal manually, then S2 | EXIT → RELINQUISHED, ringer untouched |
| S6 | Poor accuracy | Device: indoor spot deep in a building. Emulator: set the point ~60 m outside the verify radius | UNCERTAIN notification, or INSUFFICIENT; no auto change |
| S7 | Reboot inside | After S1 OWNED: `adb reboot` | BOOT → re-register → recover; session still OWNED; EXIT later restores |
| S8 | App update | `adb install -r` with fences registered | PACKAGE_REPLACED → re-register logged |
| S9 | Location toggle | Location off → on | PROVIDERS_CHANGED → re-register when on |
| S10 | Doze | Fences registered, force-idle, then S1 | Record whether DWELL/verification still happen and their latency |
| S11 | Battery saver | Saver on, then S1 | Record behaviour |
| S12 | Force stop | Force stop, then S1 | Expected: **nothing happens until next launch** (documented limitation) |
| S13 | No policy access, Vibrate | Policy access revoked; Set Vibrate on diagnostics | Record typed result (`ok` / `needs_policy_access` / `mismatch`) |
| S14 | Policy access, Silent/DND (optional path) | Grant access; Set Silent / setDnd | Record result + stream/DND snapshot deltas |
| S15 | Max duration | Set `maxSessionMs` to 2 min; S1 and stay | Notification "Still on Vibrate — restore?"; **no** automatic restore |
| S16 | Radius sweep | S1/S3 with radius 100 / 150 / 250 m | Record latency and false triggers per radius |
| S17 | TaskManager baseline | Enable baseline, repeat S1–S3 | Compare rows (ENTER/EXIT only) |

## 2. Metrics per run

Record per run (one row each):

- Transition latency: event time − route/arrival time.
- DWELL delivered y/n.
- Verification:
  - expedited y/n;
  - start delay;
  - duration;
  - fix accuracy;
  - fix age;
  - decision.
- Ringer result: typed result, plus the snapshot before/after (ring, notification, alarm, music, interruption filter).
- Recovery: re-registration success after boot / update / location toggle.
- Battery: % drop over a 24 h idle day with 5 fences registered, versus the same day without fences.

## 3. Results template (copy per configuration)

```
### Config: <Emulator API 33 Pixel 7 | Samsung S23 Android 15 | ...>
Build: <EAS build id or local>, app version <x>, Play services version <from probe>
Date: <YYYY-MM-DD>, tester: <name>

| Scenario | App state | ENTER | DWELL | Verify (exp/delay/dur/acc/age/decision) | Ringer result | Latency | Notes |
|---|---|---|---|---|---|---|---|
| S1 | swiped | y | y | y/1.2s/4.5s/12m/1s/STRONG | ok(VIBRATE) | 95 s | |
| ... | | | | | | | |

Policy access needed for VIBRATE: <y/n>
Stream/DND side effects of VIBRATE: <none / ...>
Re-register after: boot <y/n>, update <y/n>, location toggle <y/n>
Native vs TaskManager: <summary>
Battery 24h: with fences <x%>, without <y%>
```

## 4. G1 summary (fill after runs)

| Gate item | Result | Evidence |
|---|---|---|
| DWELL with app swiped away | | |
| Background fix within timeout (share of DWELLs) | | |
| VIBRATE without policy access | | |
| Alarm/media/DND side effects | | |
| Re-registration (boot/update/location) | | |
| Native vs TaskManager | | |
| **Decision for Phase 2** | | |

# HolyRadius — Decisions & Assumptions Log

Living document. Every assumption is either **verified** (with evidence) or
**open** (with the phase that resolves it).

## Product stance
- Location data cannot prove building-level presence. The app says "likely inside", never "protected".
- **Vibrate is the default and the goal.** DND/Silent is an optional opt-in that requires Notification Policy Access, which the user grants in system settings.
- An uncertain signal never turns sound back on blindly and never auto-silences. It produces a notification at most.

## Architecture decisions
| ID | Decision | Rationale | Status |
|---|---|---|---|
| D1 | Background orchestration in native Kotlin (local Expo module), not Expo TaskManager | `expo-location` 57 geofencing exposes only ENTER/EXIT (`GeofencingTaskConsumer.kt` sets `ENTER or EXIT` and `INITIAL_TRIGGER_ENTER or EXIT`), with no DWELL or loitering delay. TaskManager also needs the JS runtime to boot headless. | Provisional; confirmed or reverted at G1 |
| D2 | No app-managed, long-running foreground service | Android 12+ background-start limits; Play FGS declarations | Accepted (see A2 for the WorkManager caveat) |
| D3 | DWELL is the primary trigger; ENTER is evidence only | Filters drive-bys before any GPS work | Provisional; measured in Phase 1 |
| D4 | Pure decision logic in `domain/` with no Android imports, JUnit-tested on the JVM | Testability; this sandbox cannot compile Android code | Accepted |
| D5 | MapTiler for rendering only; Overpass for mosque data only | Separation, offline fallback | Accepted (Phase 5/6) |
| D6 | No background network in the MVP | Battery, privacy, Play policy | Accepted |

## Assumptions
| # | Assumption | Resolution / status |
|---|---|---|
| A1 | TaskManager is sufficient | Doubtful (see D1). Phase 1 runs a side-by-side baseline. **Open → G1** |
| A2 | "No foreground service" | No *app-managed* FGS. **On API < 31, expedited WorkManager runs as a foreground service** (`SystemForegroundService`, needs `getForegroundInfo()` and a notification). Phase 1 runs it **without** a location FGS type and records whether location access works on API 29–30. If it doesn't, either declare the type and record the Play impact, or use non-expedited work below API 31. **Open → G1** |
| A2b | Merged manifest has no location FGS | **False while `expo-location` is a dependency.** Its manifest declares `LocationTaskService` with `foregroundServiceType="location"`. If expo-location stays only for foreground use after G1, remove that service with a config plugin (`tools:node="remove"`) before Play submission. **Open → Phase 9** |
| A3 | 150 m geofence radius is reliable | No: it is a configurable **starting value**. Measure 100/150/250 m latency and accuracy per device. **Open → G1** |
| A4 | A background fix is obtainable after a geofence event | Expedited work does **not** bypass background-location limits and is quota-bound. Hard timeouts plus age/accuracy validation apply. Triggering-location-only evidence is capped at UNCERTAIN. **Open → G1** |
| A5 | `setRingerMode(VIBRATE)` works without DND access | Device/OS dependent. Always read back to verify. Typed results. **Open → G1** |
| A6 | Manual ringer changes are detectable | Best-effort only: compare the current mode with the persisted applied mode before restoring. Never restore without an owned session. **Accepted as limitation** |
| A7 | Geofences persist | No. Re-register on boot, package replaced, providers changed, error 1000, and stale-registry app launch. Recheck permissions and location first. No promise after Force Stop or on every OEM. **Open → G1** |
| A8 | MapTiler key can be locked to the app | No. Use the User-Agent restriction, which is not tamper-proof; separate dev/prod keys; usage monitoring and billing safeguards. MapTiler Free = non-commercial use and commercial-product R&D only. Verify offline tile licensing separately. **Open → Phase 6/9** |
| A9 | Alarms/media unaffected | Measured per stream and DND filter in M3. **Open → G1** |
| A10 | MapTiler key available | Via `EXPO_PUBLIC_MAPTILER_KEY` (`.env.local` / EAS env). Not needed before Phase 6. |
| A11 | Device testing | The user has EAS and Android Studio: emulator (Google Play image) plus a physical device. |

## Sandbox limitations (cloud dev session)
- `docs.expo.dev`, `api.expo.dev`, `dl.google.com` and `maven.google.com` (which redirects to dl.google.com) are blocked by network policy.
  - Expo APIs were checked against the installed package sources and typings instead of the online docs.
  - Android/Kotlin platform code cannot be compiled here. The first real compile is the user's EAS / Android Studio build.
  - Pure `domain/` Kotlin is compiled and unit-tested on the JVM (`native-tests/`).

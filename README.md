# HolyRadius

An Android app (React Native + Expo) that estimates when you are inside a mosque, using
OpenStreetMap data. If you opt in, it switches your phone to **Vibrate** and restores
your previous mode when you leave. When it isn't confident, it asks you with a
notification instead of acting.

> **Status: Phase 1 — native feasibility prototype.** The app currently has only a diagnostics
> screen. It does not yet use OSM data, a map, or product UI. See
> [`docs/phase-1-implementation.md`](docs/phase-1-implementation.md).

Location data cannot guarantee building-level presence. HolyRadius says "likely inside", never
"protected". It can't work after a Force Stop, and its reliability varies by device
manufacturer.

## Docs
- [`docs/phase-1-implementation.md`](docs/phase-1-implementation.md): Phase 1 modules, runtime flow, config, gate G1
- [`docs/phase-1-results.md`](docs/phase-1-results.md): emulator/device test protocol and results template
- [`docs/decisions.md`](docs/decisions.md): architecture decisions and assumption log

## Layout
```
modules/holy-radius-native/   local Expo module (Kotlin): geofencing, verification, ringer session
  android/.../domain/         pure Kotlin decision logic (no Android imports)
native-tests/                 JVM JUnit harness for domain/
src/app/                      Expo Router screens (Phase 1: diagnostics only)
src/features/                 diagnostics UI, TaskManager baseline
src/config/                   centralized TS config (mirrors PrototypeConfig.kt)
```

## Build & run (Expo SDK 57)
Requires a **development build**. Expo Go cannot load the native module.

```bash
npm install

# Emulator (Android Studio, use a *Google Play* system image)
npx expo run:android

# Physical device via EAS
eas build --profile development --platform android   # install the APK
npx expo start --dev-client
```

## Checks
```bash
npm run typecheck        # tsc --noEmit
npm test                 # Jest: TS helpers + TS/Kotlin config parity
npm run test:native      # JUnit on the pure Kotlin domain (needs Gradle + JDK 17+)
```

## MapTiler key (needed from Phase 6, the map)
Never commit the key. Put it in `.env.local` (git-ignored) or in EAS env:

```bash
# .env.local
EXPO_PUBLIC_MAPTILER_KEY=your_dev_key

# EAS
eas env:create --name EXPO_PUBLIC_MAPTILER_KEY --value <dev_key> --environment development --visibility sensitive
```

- Use separate dev and prod keys, each with a User-Agent restriction (not tamper-proof).
- Turn on usage alerts and billing safeguards.
- MapTiler Free covers non-commercial use and commercial-product R&D only.

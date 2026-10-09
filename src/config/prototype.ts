import type { PrototypeConfig } from '../../modules/holy-radius-native';

/**
 * TS mirror of PrototypeConfig.kt defaults (Phase 1). Starting values to measure, not tuned values.
 * The native side is the source of truth at runtime (getConfig/setConfig).
 */
export const DEFAULT_PROTOTYPE_CONFIG: PrototypeConfig = {
  defaultRadiusM: 150,
  defaultLoiterMs: 90_000,
  responsivenessMs: 30_000,
  defaultVerifyRadiusM: 30,
  verifyOnEnter: false,
  verifyTimeoutMs: 20_000,
  verifyMaxAttempts: 2,
  maxFixAgeMs: 30_000,
  strongAccuracyM: 35,
  hardMaxAccuracyM: 120,
  dedupWindowMs: 60_000,
  verifyCooldownMs: 600_000,
  maxVerificationsPerHour: 6,
  maxSessionMs: 3 * 60 * 60 * 1000,
};

/** Input bounds for test fences on the diagnostics screen. */
export const FENCE_LIMITS = {
  minRadiusM: 50,
  maxRadiusM: 1000,
  minLoiterMs: 0,
  maxLoiterMs: 30 * 60 * 1000,
  minVerifyRadiusM: 5,
  maxVerifyRadiusM: 200,
  /** Android allows 100 geofences per app; the TaskManager baseline uses the same budget. */
  maxFences: 20,
} as const;

/** How often the diagnostics screen polls the native log while visible. */
export const LOG_POLL_MS = 2_000;
export const LOG_VIEW_MAX = 300;

// Typed contract of the HolyRadiusNative bridge (Phase 1). Mirrors the Kotlin side in
// android/src/main/java/com/holyradius/nativecore/HolyRadiusNativeModule.kt.

export type RingerModeWire = 'normal' | 'vibrate' | 'silent';
export type InterruptionFilterWire = 'all' | 'priority' | 'alarms' | 'none';

export type RingerSetResult =
  | { result: 'ok'; observed: RingerModeWire }
  | { result: 'needs_policy_access' }
  | { result: 'mismatch'; requested: RingerModeWire; observed: RingerModeWire }
  | { result: 'failed'; reason: string };

export type StreamVolume = { vol: number; max: number };

export type RingerSnapshot = {
  ringerMode: RingerModeWire;
  ring: StreamVolume;
  notification: StreamVolume;
  alarm: StreamVolume;
  music: StreamVolume;
  interruptionFilter: string;
  policyAccess: boolean;
};

export type Probe = {
  apiLevel: number;
  release: string;
  manufacturer: string;
  model: string;
  fineLocation: boolean;
  coarseLocation: boolean;
  backgroundLocation: boolean;
  notificationsEnabled: boolean;
  policyAccess: boolean;
  locationEnabled: boolean;
  playServices: { available: boolean; statusCode: number; version: number | null };
  ignoringBatteryOptimizations: boolean;
  powerSaveMode: boolean;
  standbyBucket: string | null;
  bootCount: number | null;
  packageUpdateTime: number;
};

export type FenceSpec = {
  id: string;
  lat: number;
  lng: number;
  radiusM: number;
  loiterMs: number;
  verifyRadiusM: number;
};

export type RegistryStatus = 'none' | 'registered' | 'failed' | 'not_available' | 'skipped_precheck';

export type Registry = {
  fences: FenceSpec[];
  status: RegistryStatus;
  lastError: string | null;
  lastAttemptAt: number | null;
  bootCount: number | null;
  packageUpdateTime: number | null;
};

export type RegisterResult =
  | { result: 'registered'; count: number }
  | { result: 'skipped'; reason: string }
  | { result: 'failed'; code: number | null; name: string };

export type Ownership = 'pending' | 'owned' | 'relinquished' | 'ended';

export type Session = {
  sessionId: string;
  fenceId: string;
  previousMode: RingerModeWire;
  appliedMode: RingerModeWire;
  startedAt: number;
  maxUntil: number;
  ownership: Ownership;
  expiryNotified: boolean;
  ownershipValid: boolean;
};

/** Results are open-ended maps; `result` is always present. */
export type SessionActionResult = { result: string; [key: string]: unknown };

export type LaunchReconcile = {
  decision: 'needed' | 'not_needed' | 'blocked';
  reason: string;
  recover: SessionActionResult;
};

export type LogSource = 'native' | 'taskmanager' | 'js';

export type LogEntry = {
  id: number;
  t: number;
  et: number;
  source: LogSource;
  type: string;
  proc: 'fg' | 'bg';
  coldStart?: boolean;
  payload: Record<string, unknown>;
};

export type PrototypeConfig = {
  defaultRadiusM: number;
  defaultLoiterMs: number;
  responsivenessMs: number;
  defaultVerifyRadiusM: number;
  verifyOnEnter: boolean;
  verifyTimeoutMs: number;
  verifyMaxAttempts: number;
  maxFixAgeMs: number;
  strongAccuracyM: number;
  hardMaxAccuracyM: number;
  dedupWindowMs: number;
  verifyCooldownMs: number;
  maxVerificationsPerHour: number;
  maxSessionMs: number;
};

/** Raw native surface (JSON strings where noted). Use the wrappers in index.ts instead. */
export interface HolyRadiusNativeRaw {
  ping(): { pong: boolean; apiLevel: number };
  probe(): Promise<Probe>;
  getLog(sinceId: number, limit: number): Promise<string[]>;
  clearLog(): Promise<boolean>;
  logEvent(source: string, type: string, payloadJson: string): Promise<void>;
  exportLog(): Promise<string>;
  ringerSnapshot(): Promise<RingerSnapshot>;
  setRingerMode(mode: RingerModeWire): Promise<RingerSetResult>;
  setInterruptionFilter(filter: InterruptionFilterWire): Promise<{ result: string; observed?: string; reason?: string }>;
  openPolicyAccessSettings(): Promise<void>;
  openAppSettings(): Promise<void>;
  openLocationSettings(): Promise<void>;
  registerFences(fencesJson: string): Promise<RegisterResult>;
  unregisterAll(): Promise<boolean>;
  getRegistry(): Promise<string>;
  forceReRegister(): Promise<void>;
  reconcileOnLaunch(): Promise<LaunchReconcile>;
  triggerVerification(fenceId: string): Promise<void>;
  getSession(): Promise<Session | null>;
  manualVibrate(): Promise<SessionActionResult>;
  manualRestore(): Promise<SessionActionResult>;
  recoverSession(): Promise<SessionActionResult>;
  getConfig(): Promise<string>;
  setConfig(configJson: string): Promise<string>;
  resetConfig(): Promise<string>;
}

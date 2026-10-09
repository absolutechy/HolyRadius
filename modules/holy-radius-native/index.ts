import { nativeModule } from './src/HolyRadiusNativeModule';
import type {
  FenceSpec,
  HolyRadiusNativeRaw,
  LogEntry,
  LogSource,
  PrototypeConfig,
  Registry,
} from './src/HolyRadiusNative.types';

export * from './src/HolyRadiusNative.types';

export class NativeUnavailableError extends Error {
  constructor() {
    super('HolyRadiusNative is not available. Use a development build (expo-dev-client), not Expo Go.');
    this.name = 'NativeUnavailableError';
  }
}

export const isNativeAvailable = (): boolean => nativeModule != null;

function native(): HolyRadiusNativeRaw {
  if (!nativeModule) throw new NativeUnavailableError();
  return nativeModule;
}

/** Parses JSONL log lines, skipping any corrupt line instead of failing the whole read. */
export function parseLogLines(lines: string[]): LogEntry[] {
  const out: LogEntry[] = [];
  for (const line of lines) {
    try {
      out.push(JSON.parse(line) as LogEntry);
    } catch {
      // ignore partial/corrupt line
    }
  }
  return out;
}

export const HolyRadiusNative = {
  ping: () => native().ping(),
  probe: () => native().probe(),
  getLog: async (sinceId = 0, limit = 500): Promise<LogEntry[]> =>
    parseLogLines(await native().getLog(sinceId, limit)),
  clearLog: () => native().clearLog(),
  logEvent: (source: LogSource, type: string, payload: Record<string, unknown> = {}) =>
    native().logEvent(source, type, JSON.stringify(payload)),
  exportLog: () => native().exportLog(),

  ringerSnapshot: () => native().ringerSnapshot(),
  setRingerMode: (mode: Parameters<HolyRadiusNativeRaw['setRingerMode']>[0]) => native().setRingerMode(mode),
  setInterruptionFilter: (f: Parameters<HolyRadiusNativeRaw['setInterruptionFilter']>[0]) =>
    native().setInterruptionFilter(f),
  openPolicyAccessSettings: () => native().openPolicyAccessSettings(),
  openAppSettings: () => native().openAppSettings(),
  openLocationSettings: () => native().openLocationSettings(),

  registerFences: (fences: FenceSpec[]) => native().registerFences(JSON.stringify(fences)),
  unregisterAll: () => native().unregisterAll(),
  getRegistry: async (): Promise<Registry> => JSON.parse(await native().getRegistry()) as Registry,
  forceReRegister: () => native().forceReRegister(),
  reconcileOnLaunch: () => native().reconcileOnLaunch(),
  triggerVerification: (fenceId: string) => native().triggerVerification(fenceId),

  getSession: () => native().getSession(),
  manualVibrate: () => native().manualVibrate(),
  manualRestore: () => native().manualRestore(),
  recoverSession: () => native().recoverSession(),

  getConfig: async (): Promise<PrototypeConfig> => JSON.parse(await native().getConfig()) as PrototypeConfig,
  setConfig: async (patch: Partial<PrototypeConfig>): Promise<PrototypeConfig> =>
    JSON.parse(await native().setConfig(JSON.stringify(patch))) as PrototypeConfig,
  resetConfig: async (): Promise<PrototypeConfig> => JSON.parse(await native().resetConfig()) as PrototypeConfig,
};

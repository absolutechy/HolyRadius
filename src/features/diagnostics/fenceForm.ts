import type { FenceSpec, PrototypeConfig } from '../../../modules/holy-radius-native';
import { FENCE_LIMITS } from '../../config/prototype';

export type FenceInput = {
  lat: string;
  lng: string;
  radiusM?: string;
  loiterSec?: string;
  verifyRadiusM?: string;
};

export type FenceParse = { ok: true; fence: FenceSpec } | { ok: false; errors: string[] };

function num(value: string | undefined): number | undefined {
  if (value == null || value.trim() === '') return undefined;
  const n = Number(value.trim());
  return Number.isFinite(n) ? n : NaN;
}

/** Validates diagnostics-screen input into a FenceSpec; blank optional fields use config defaults. */
export function parseFenceInput(input: FenceInput, cfg: PrototypeConfig, id: string): FenceParse {
  const errors: string[] = [];
  const lat = num(input.lat);
  const lng = num(input.lng);
  const radiusM = num(input.radiusM) ?? cfg.defaultRadiusM;
  const loiterSec = num(input.loiterSec);
  const loiterMs = loiterSec === undefined ? cfg.defaultLoiterMs : Math.round(loiterSec * 1000);
  const verifyRadiusM = num(input.verifyRadiusM) ?? cfg.defaultVerifyRadiusM;

  if (lat === undefined || Number.isNaN(lat) || lat < -90 || lat > 90) errors.push('Latitude must be between -90 and 90');
  if (lng === undefined || Number.isNaN(lng) || lng < -180 || lng > 180) errors.push('Longitude must be between -180 and 180');
  if (Number.isNaN(radiusM) || radiusM < FENCE_LIMITS.minRadiusM || radiusM > FENCE_LIMITS.maxRadiusM) {
    errors.push(`Radius must be ${FENCE_LIMITS.minRadiusM}–${FENCE_LIMITS.maxRadiusM} m`);
  }
  if (Number.isNaN(loiterMs) || loiterMs < FENCE_LIMITS.minLoiterMs || loiterMs > FENCE_LIMITS.maxLoiterMs) {
    errors.push(`Loiter must be 0–${FENCE_LIMITS.maxLoiterMs / 1000} s`);
  }
  if (
    Number.isNaN(verifyRadiusM) ||
    verifyRadiusM < FENCE_LIMITS.minVerifyRadiusM ||
    verifyRadiusM > FENCE_LIMITS.maxVerifyRadiusM
  ) {
    errors.push(`Verify radius must be ${FENCE_LIMITS.minVerifyRadiusM}–${FENCE_LIMITS.maxVerifyRadiusM} m`);
  } else if (!Number.isNaN(radiusM) && verifyRadiusM > radiusM) {
    errors.push('Verify radius cannot exceed the geofence radius');
  }

  if (errors.length > 0) return { ok: false, errors };
  return { ok: true, fence: { id, lat: lat!, lng: lng!, radiusM, loiterMs, verifyRadiusM } };
}

/** Short, sortable, human-readable test fence id. */
export function makeFenceId(now: number = Date.now()): string {
  return `test-${now.toString(36)}`;
}

/** Adds or replaces a fence by id, enforcing the prototype fence budget. */
export function upsertFence(fences: FenceSpec[], fence: FenceSpec): { ok: true; fences: FenceSpec[] } | { ok: false; error: string } {
  const without = fences.filter((f) => f.id !== fence.id);
  if (without.length >= FENCE_LIMITS.maxFences) {
    return { ok: false, error: `At most ${FENCE_LIMITS.maxFences} test fences` };
  }
  return { ok: true, fences: [...without, fence] };
}

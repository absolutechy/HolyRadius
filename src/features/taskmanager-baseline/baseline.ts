import * as Location from 'expo-location';
import * as TaskManager from 'expo-task-manager';

import { HolyRadiusNative, isNativeAvailable, type FenceSpec } from '../../../modules/holy-radius-native';

/**
 * M10: Expo TaskManager baseline. Same test points as the native pipeline (ids prefixed "tm:"),
 * logged to the same EventLog with source "taskmanager" so the two can be compared row by row.
 * Expo geofencing only supports ENTER/EXIT (no DWELL / loitering delay) — that gap is part of the comparison.
 */
export const BASELINE_TASK = 'holyradius-tm-baseline-geofence';
export const BASELINE_PREFIX = 'tm:';

type GeofencingTaskData = {
  eventType: Location.GeofencingEventType;
  region: Location.LocationRegion;
};

export function toBaselineRegions(fences: FenceSpec[]): Location.LocationRegion[] {
  return fences.map((f) => ({
    identifier: `${BASELINE_PREFIX}${f.id}`,
    latitude: f.lat,
    longitude: f.lng,
    radius: f.radiusM,
    notifyOnEnter: true,
    notifyOnExit: true,
  }));
}

export function eventTypeName(type: Location.GeofencingEventType): 'enter' | 'exit' | 'unknown' {
  if (type === Location.GeofencingEventType.Enter) return 'enter';
  if (type === Location.GeofencingEventType.Exit) return 'exit';
  return 'unknown';
}

async function log(type: string, payload: Record<string, unknown>): Promise<void> {
  if (!isNativeAvailable()) return;
  try {
    await HolyRadiusNative.logEvent('taskmanager', type, payload);
  } catch {
    // logging must never crash a headless task
  }
}

/** Must run at module scope of the app entry so headless starts can find the task. */
export function defineBaselineTask(): void {
  if (TaskManager.isTaskDefined(BASELINE_TASK)) return;
  TaskManager.defineTask<GeofencingTaskData>(BASELINE_TASK, async ({ data, error }) => {
    if (error) {
      await log('tm_geofence_error', { message: error.message });
      return;
    }
    await log('tm_geofence', {
      transition: eventTypeName(data.eventType),
      fenceId: data.region.identifier ?? null,
      regionState: data.region.state ?? null,
    });
  });
}

export async function startBaseline(fences: FenceSpec[]): Promise<void> {
  await Location.startGeofencingAsync(BASELINE_TASK, toBaselineRegions(fences));
  await log('tm_baseline_started', { count: fences.length });
}

export async function stopBaseline(): Promise<void> {
  if (await Location.hasStartedGeofencingAsync(BASELINE_TASK)) {
    await Location.stopGeofencingAsync(BASELINE_TASK);
  }
  await log('tm_baseline_stopped', {});
}

export function isBaselineRunning(): Promise<boolean> {
  return Location.hasStartedGeofencingAsync(BASELINE_TASK);
}

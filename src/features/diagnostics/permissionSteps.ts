import type { Probe } from '../../../modules/holy-radius-native';

/**
 * Progressive permission order for Automatic Mode. DND / Notification Policy Access is deliberately
 * NOT a step: it is optional and only offered via an explicit button.
 */
export type PermissionStep = 'foreground_location' | 'background_location' | 'notifications' | 'done';

export function nextPermissionStep(probe: Pick<Probe, 'apiLevel' | 'fineLocation' | 'backgroundLocation' | 'notificationsEnabled'>): PermissionStep {
  if (!probe.fineLocation) return 'foreground_location';
  if (!probe.backgroundLocation) return 'background_location';
  if (probe.apiLevel >= 33 && !probe.notificationsEnabled) return 'notifications';
  return 'done';
}

export const PERMISSION_STEP_COPY: Record<PermissionStep, { title: string; body: string }> = {
  foreground_location: {
    title: 'Allow precise location',
    body: 'Needed to place test geofences and to check whether you are near a test point.',
  },
  background_location: {
    title: 'Allow location “All the time”',
    body:
      'Geofence events and the short verification check run while the app is closed. ' +
      'Android shows this as a separate settings page. Location is processed on the device only.',
  },
  notifications: {
    title: 'Allow notifications',
    body: 'Used to ask before switching to Vibrate when presence is uncertain, and to offer “Restore sound”.',
  },
  done: { title: 'All required permissions granted', body: '' },
};

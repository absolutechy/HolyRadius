import { nextPermissionStep } from '../features/diagnostics/permissionSteps';

const base = { apiLevel: 34, fineLocation: true, backgroundLocation: true, notificationsEnabled: true };

describe('nextPermissionStep', () => {
  it('asks foreground before background before notifications', () => {
    expect(nextPermissionStep({ ...base, fineLocation: false, backgroundLocation: false })).toBe('foreground_location');
    expect(nextPermissionStep({ ...base, backgroundLocation: false })).toBe('background_location');
    expect(nextPermissionStep({ ...base, notificationsEnabled: false })).toBe('notifications');
    expect(nextPermissionStep(base)).toBe('done');
  });

  it('does not request the notification runtime permission below API 33', () => {
    expect(nextPermissionStep({ ...base, apiLevel: 32, notificationsEnabled: false })).toBe('done');
  });
});

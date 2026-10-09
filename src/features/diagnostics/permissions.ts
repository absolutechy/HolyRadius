import { PermissionsAndroid, Platform } from 'react-native';

import type { PermissionStep } from './permissionSteps';

/** Requests exactly one step. Returns the raw Android result for logging. */
export async function requestPermissionStep(step: PermissionStep): Promise<string> {
  if (Platform.OS !== 'android') return 'unsupported_platform';
  const P = PermissionsAndroid.PERMISSIONS;
  switch (step) {
    case 'foreground_location': {
      const r = await PermissionsAndroid.requestMultiple([P.ACCESS_FINE_LOCATION, P.ACCESS_COARSE_LOCATION]);
      return r[P.ACCESS_FINE_LOCATION];
    }
    case 'background_location':
      return PermissionsAndroid.request(P.ACCESS_BACKGROUND_LOCATION);
    case 'notifications':
      return PermissionsAndroid.request(P.POST_NOTIFICATIONS);
    case 'done':
      return 'nothing_to_request';
  }
}

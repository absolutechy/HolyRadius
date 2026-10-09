import { requireOptionalNativeModule } from 'expo';

import type { HolyRadiusNativeRaw } from './HolyRadiusNative.types';

/** null when running without the dev build (Expo Go, web, Jest). */
export const nativeModule = requireOptionalNativeModule<HolyRadiusNativeRaw>('HolyRadiusNative');

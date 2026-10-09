import { readFileSync } from 'fs';
import { join } from 'path';

import { DEFAULT_PROTOTYPE_CONFIG } from '../config/prototype';

/** Keeps src/config/prototype.ts in sync with the native source of truth (PrototypeConfig.kt). */
it('TS prototype defaults match PrototypeConfig.kt', () => {
  const kt = readFileSync(
    join(__dirname, '../../modules/holy-radius-native/android/src/main/java/com/holyradius/nativecore/config/PrototypeConfig.kt'),
    'utf8',
  );
  const native: Record<string, number | boolean> = {};
  for (const m of kt.matchAll(/val (\w+): \w+ = ([^,\n]+)/g)) {
    const expr = m[2].trim().replace(/_/g, '').replace(/L\b/g, '');
    native[m[1]] = expr === 'true' || expr === 'false' ? expr === 'true' : Function(`return (${expr})`)();
  }
  expect(native).toEqual(DEFAULT_PROTOTYPE_CONFIG);
});

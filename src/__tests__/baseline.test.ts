import { BASELINE_PREFIX, toBaselineRegions } from '../features/taskmanager-baseline/baseline';

it('maps fences to expo-location regions with the tm: prefix', () => {
  expect(toBaselineRegions([{ id: 'a', lat: 1, lng: 2, radiusM: 150, loiterMs: 0, verifyRadiusM: 30 }])).toEqual([
    { identifier: `${BASELINE_PREFIX}a`, latitude: 1, longitude: 2, radius: 150, notifyOnEnter: true, notifyOnExit: true },
  ]);
});

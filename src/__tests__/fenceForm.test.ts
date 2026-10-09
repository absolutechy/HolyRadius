import { DEFAULT_PROTOTYPE_CONFIG as cfg, FENCE_LIMITS } from '../config/prototype';
import { makeFenceId, parseFenceInput, upsertFence } from '../features/diagnostics/fenceForm';

describe('parseFenceInput', () => {
  it('uses config defaults for blank optional fields', () => {
    const r = parseFenceInput({ lat: '51.5', lng: '-0.12' }, cfg, 'a');
    expect(r).toEqual({
      ok: true,
      fence: { id: 'a', lat: 51.5, lng: -0.12, radiusM: 150, loiterMs: 90_000, verifyRadiusM: 30 },
    });
  });

  it('converts loiter seconds to ms', () => {
    const r = parseFenceInput({ lat: '0', lng: '0', loiterSec: '45' }, cfg, 'a');
    expect(r.ok && r.fence.loiterMs).toBe(45_000);
  });

  it('rejects out-of-range and non-numeric values', () => {
    const r = parseFenceInput({ lat: '91', lng: 'abc', radiusM: '10' }, cfg, 'a');
    expect(r.ok).toBe(false);
    if (!r.ok) {
      expect(r.errors).toEqual(
        expect.arrayContaining([
          expect.stringContaining('Latitude'),
          expect.stringContaining('Longitude'),
          expect.stringContaining('Radius'),
        ]),
      );
    }
  });

  it('rejects a verify radius larger than the geofence', () => {
    const r = parseFenceInput({ lat: '0', lng: '0', radiusM: '60', verifyRadiusM: '100' }, cfg, 'a');
    expect(r.ok).toBe(false);
  });
});

describe('upsertFence', () => {
  const f = (id: string) => ({ id, lat: 0, lng: 0, radiusM: 150, loiterMs: 0, verifyRadiusM: 30 });

  it('replaces by id', () => {
    const r = upsertFence([f('a'), f('b')], { ...f('a'), radiusM: 200 });
    expect(r.ok && r.fences.map((x) => [x.id, x.radiusM])).toEqual([
      ['b', 150],
      ['a', 200],
    ]);
  });

  it('enforces the fence budget', () => {
    const many = Array.from({ length: FENCE_LIMITS.maxFences }, (_, i) => f(`f${i}`));
    expect(upsertFence(many, f('new')).ok).toBe(false);
    expect(upsertFence(many, f('f0')).ok).toBe(true);
  });
});

it('makeFenceId is prefixed and deterministic', () => {
  expect(makeFenceId(36)).toBe('test-10');
});

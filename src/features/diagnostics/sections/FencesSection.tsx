import * as Location from 'expo-location';
import { useState } from 'react';
import { Text, View } from 'react-native';

import { HolyRadiusNative, type PrototypeConfig, type Registry } from '../../../../modules/holy-radius-native';
import { makeFenceId, parseFenceInput, upsertFence, type FenceInput } from '../fenceForm';
import { Button, Field, KV, Row, Section, styles } from '../ui';
import type { Run } from '../useRunner';

type Props = { registry: Registry | null; config: PrototypeConfig | null; run: Run; refresh: () => Promise<void> };

export function FencesSection({ registry, config, run, refresh }: Props) {
  const [input, setInput] = useState<FenceInput>({ lat: '', lng: '', radiusM: '', loiterSec: '', verifyRadiusM: '' });
  const [errors, setErrors] = useState<string[]>([]);
  const set = (k: keyof FenceInput) => (v: string) => setInput((s) => ({ ...s, [k]: v }));

  const useCurrentLocation = () =>
    run('current location', async () => {
      const pos = await Location.getCurrentPositionAsync({ accuracy: Location.Accuracy.High });
      setInput((s) => ({ ...s, lat: pos.coords.latitude.toFixed(6), lng: pos.coords.longitude.toFixed(6) }));
      return { lat: pos.coords.latitude, lng: pos.coords.longitude, accuracyM: pos.coords.accuracy };
    });

  const register = () => {
    if (!config || !registry) return;
    const parsed = parseFenceInput(input, config, makeFenceId());
    if (!parsed.ok) {
      setErrors(parsed.errors);
      return;
    }
    const next = upsertFence(registry.fences, parsed.fence);
    if (!next.ok) {
      setErrors([next.error]);
      return;
    }
    setErrors([]);
    run('register fences', async () => {
      const r = await HolyRadiusNative.registerFences(next.fences);
      await refresh();
      return r;
    });
  };

  const remove = (id: string) =>
    run(`remove ${id}`, async () => {
      const r = await HolyRadiusNative.registerFences((registry?.fences ?? []).filter((f) => f.id !== id));
      await refresh();
      return r;
    });

  return (
    <Section title="Geofences (M4–M7)">
      {registry ? (
        <>
          <KV k="status" v={`${registry.status}${registry.lastError ? ` (${registry.lastError})` : ''}`} />
          <KV k="last attempt" v={registry.lastAttemptAt ? new Date(registry.lastAttemptAt).toLocaleString() : '—'} />
          {registry.fences.map((f) => (
            <View key={f.id} style={{ gap: 4 }}>
              <Text style={styles.kv}>
                {f.id} · {f.lat.toFixed(5)},{f.lng.toFixed(5)} · r={f.radiusM}m · loiter={f.loiterMs / 1000}s · verify={f.verifyRadiusM}m
              </Text>
              <Row>
                <Button label="Verify now" onPress={() => run(`verify ${f.id}`, () => HolyRadiusNative.triggerVerification(f.id))} />
                <Button label="Remove" onPress={() => remove(f.id)} />
              </Row>
            </View>
          ))}
        </>
      ) : null}
      <Row>
        <Field label="lat" value={input.lat} onChangeText={set('lat')} keyboardType="numeric" />
        <Field label="lng" value={input.lng} onChangeText={set('lng')} keyboardType="numeric" />
      </Row>
      <Row>
        <Field label={`radius m (${config?.defaultRadiusM ?? '…'})`} value={input.radiusM} onChangeText={set('radiusM')} keyboardType="numeric" />
        <Field
          label={`loiter s (${config ? config.defaultLoiterMs / 1000 : '…'})`}
          value={input.loiterSec}
          onChangeText={set('loiterSec')}
          keyboardType="numeric"
        />
        <Field
          label={`verify m (${config?.defaultVerifyRadiusM ?? '…'})`}
          value={input.verifyRadiusM}
          onChangeText={set('verifyRadiusM')}
          keyboardType="numeric"
        />
      </Row>
      {errors.map((e) => (
        <Text key={e} style={styles.error}>
          {e}
        </Text>
      ))}
      <Row>
        <Button label="Use current location" onPress={useCurrentLocation} />
        <Button label="Add & register" onPress={register} disabled={!config || !registry} />
      </Row>
      <Row>
        <Button
          label="Unregister all"
          onPress={() =>
            run('unregister all', async () => {
              const r = await HolyRadiusNative.unregisterAll();
              await refresh();
              return r;
            })
          }
        />
        <Button label="Force re-register" onPress={() => run('force re-register', HolyRadiusNative.forceReRegister)} />
      </Row>
    </Section>
  );
}

import { useCallback, useEffect, useState } from 'react';
import { ScrollView, StyleSheet, Text } from 'react-native';

import {
  HolyRadiusNative,
  isNativeAvailable,
  type Probe,
  type PrototypeConfig,
  type Registry,
  type Session,
} from '../../modules/holy-radius-native';
import { BaselineSection } from '../features/diagnostics/sections/BaselineSection';
import { ConfigSection } from '../features/diagnostics/sections/ConfigSection';
import { FencesSection } from '../features/diagnostics/sections/FencesSection';
import { LogSection } from '../features/diagnostics/sections/LogSection';
import { PermissionsSection } from '../features/diagnostics/sections/PermissionsSection';
import { RingerSection } from '../features/diagnostics/sections/RingerSection';
import { SessionSection } from '../features/diagnostics/sections/SessionSection';
import { KV, Mono, Section, styles } from '../features/diagnostics/ui';
import { useRunner } from '../features/diagnostics/useRunner';
import { isBaselineRunning } from '../features/taskmanager-baseline/baseline';

/** Phase 1 diagnostics screen (M9). Not product UI. */
export default function Diagnostics() {
  const { last, busy, run } = useRunner();
  const [probe, setProbe] = useState<Probe | null>(null);
  const [registry, setRegistry] = useState<Registry | null>(null);
  const [session, setSession] = useState<Session | null>(null);
  const [config, setConfig] = useState<PrototypeConfig | null>(null);
  const [baseline, setBaseline] = useState<boolean | null>(null);

  const refreshAll = useCallback(async () => {
    const [p, r, s, c, b] = await Promise.all([
      HolyRadiusNative.probe(),
      HolyRadiusNative.getRegistry(),
      HolyRadiusNative.getSession(),
      HolyRadiusNative.getConfig(),
      isBaselineRunning().catch(() => null),
    ]);
    setProbe(p);
    setRegistry(r);
    setSession(s);
    setConfig(c);
    setBaseline(b);
  }, []);

  useEffect(() => {
    if (!isNativeAvailable()) return;
    void run('launch reconcile', async () => {
      const r = await HolyRadiusNative.reconcileOnLaunch();
      await refreshAll();
      return r;
    });
  }, [run, refreshAll]);

  if (!isNativeAvailable()) {
    return (
      <ScrollView contentContainerStyle={local.container}>
        <Text style={styles.error}>
          HolyRadiusNative is not available. Install the development build (eas build --profile development) — Expo Go
          cannot load the native module.
        </Text>
      </ScrollView>
    );
  }

  return (
    <ScrollView contentContainerStyle={local.container} keyboardShouldPersistTaps="handled">
      <Section title={busy ? 'Last result (running…)' : 'Last result'}>
        {last ? (
          <>
            <KV k={last.ok ? 'ok' : 'error'} v={`${last.label} @ ${new Date(last.at).toLocaleTimeString()}`} />
            <Mono>{JSON.stringify(last.value, null, 1)}</Mono>
          </>
        ) : (
          <Text style={styles.note}>—</Text>
        )}
      </Section>
      <PermissionsSection probe={probe} run={run} refresh={refreshAll} />
      <RingerSection run={run} />
      <FencesSection registry={registry} config={config} run={run} refresh={refreshAll} />
      <SessionSection session={session} run={run} refresh={refreshAll} />
      <BaselineSection running={baseline} registry={registry} run={run} refresh={refreshAll} />
      <ConfigSection config={config} run={run} refresh={refreshAll} />
      <LogSection run={run} />
    </ScrollView>
  );
}

const local = StyleSheet.create({
  container: { padding: 12, backgroundColor: '#f2f4f3' },
});

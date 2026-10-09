import { Text } from 'react-native';

import { HolyRadiusNative, type Probe } from '../../../../modules/holy-radius-native';
import { nextPermissionStep, PERMISSION_STEP_COPY } from '../permissionSteps';
import { requestPermissionStep } from '../permissions';
import { Button, KV, Row, Section, styles } from '../ui';
import type { Run } from '../useRunner';

export function PermissionsSection({ probe, run, refresh }: { probe: Probe | null; run: Run; refresh: () => Promise<void> }) {
  const step = probe ? nextPermissionStep(probe) : null;
  const copy = step ? PERMISSION_STEP_COPY[step] : null;

  return (
    <Section title="Probe & permissions (M2)">
      {probe ? (
        <>
          <KV k="device" v={`${probe.manufacturer} ${probe.model} · Android ${probe.release} (API ${probe.apiLevel})`} />
          <KV k="location" v={`fine=${probe.fineLocation} background=${probe.backgroundLocation} enabled=${probe.locationEnabled}`} />
          <KV k="notifications" v={probe.notificationsEnabled} />
          <KV k="DND access (optional)" v={probe.policyAccess} />
          <KV k="play services" v={probe.playServices} />
          <KV k="battery" v={`exempt=${probe.ignoringBatteryOptimizations} saver=${probe.powerSaveMode} bucket=${probe.standbyBucket}`} />
          <KV k="bootCount" v={probe.bootCount} />
        </>
      ) : (
        <Text style={styles.note}>No probe yet.</Text>
      )}
      {copy && step !== 'done' ? (
        <>
          <Text style={styles.k}>Next: {copy.title}</Text>
          <Text style={styles.note}>{copy.body}</Text>
        </>
      ) : null}
      <Row>
        <Button label="Refresh probe" onPress={() => run('probe', refresh)} />
        {step && step !== 'done' ? (
          <Button
            label={`Request: ${step.replace('_', ' ')}`}
            onPress={() =>
              run(`request ${step}`, async () => {
                const r = await requestPermissionStep(step);
                await HolyRadiusNative.logEvent('js', 'permission_request', { step, result: r });
                await refresh();
                return r;
              })
            }
          />
        ) : null}
        <Button label="App settings" onPress={() => run('open app settings', HolyRadiusNative.openAppSettings)} />
        <Button label="Location settings" onPress={() => run('open location settings', HolyRadiusNative.openLocationSettings)} />
        <Button label="DND access (optional)" onPress={() => run('open DND access', HolyRadiusNative.openPolicyAccessSettings)} />
      </Row>
    </Section>
  );
}

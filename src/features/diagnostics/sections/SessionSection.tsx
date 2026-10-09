import { Text } from 'react-native';

import { HolyRadiusNative, type Session } from '../../../../modules/holy-radius-native';
import { Button, KV, Row, Section, styles } from '../ui';
import type { Run } from '../useRunner';

export function SessionSection({ session, run, refresh }: { session: Session | null; run: Run; refresh: () => Promise<void> }) {
  const act = (label: string, fn: () => Promise<unknown>) =>
    run(label, async () => {
      const r = await fn();
      await refresh();
      return r;
    });

  return (
    <Section title="Ringer session (M8)">
      {session ? (
        <>
          <KV k="ownership" v={session.ownership} />
          <KV k="fence" v={session.fenceId} />
          <KV k="previous → applied" v={`${session.previousMode} → ${session.appliedMode}`} />
          <KV k="started" v={new Date(session.startedAt).toLocaleString()} />
          <KV k="max until" v={new Date(session.maxUntil).toLocaleString()} />
        </>
      ) : (
        <Text style={styles.note}>No session. HolyRadius will not restore anything.</Text>
      )}
      <Row>
        <Button label="Vibrate now (manual)" onPress={() => act('manual vibrate', HolyRadiusNative.manualVibrate)} />
        <Button label="Restore sound" onPress={() => act('manual restore', HolyRadiusNative.manualRestore)} />
        <Button label="Run recover" onPress={() => act('recover', HolyRadiusNative.recoverSession)} />
      </Row>
    </Section>
  );
}

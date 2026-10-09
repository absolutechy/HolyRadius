import { useState } from 'react';

import { HolyRadiusNative, type RingerSnapshot } from '../../../../modules/holy-radius-native';
import { Button, KV, Row, Section } from '../ui';
import type { Run } from '../useRunner';

export function RingerSection({ run }: { run: Run }) {
  const [snap, setSnap] = useState<RingerSnapshot | null>(null);
  const refresh = async () => {
    const s = await HolyRadiusNative.ringerSnapshot();
    setSnap(s);
    return s;
  };
  const set = (mode: 'normal' | 'vibrate' | 'silent') =>
    run(`set ${mode}`, async () => {
      const r = await HolyRadiusNative.setRingerMode(mode);
      await refresh();
      return r;
    });

  return (
    <Section title="Ringer (M3)">
      {snap ? (
        <>
          <KV k="mode" v={snap.ringerMode} />
          <KV k="ring / notif" v={`${snap.ring.vol}/${snap.ring.max} · ${snap.notification.vol}/${snap.notification.max}`} />
          <KV k="alarm / music" v={`${snap.alarm.vol}/${snap.alarm.max} · ${snap.music.vol}/${snap.music.max}`} />
          <KV k="DND filter" v={`${snap.interruptionFilter} (access=${snap.policyAccess})`} />
        </>
      ) : null}
      <Row>
        <Button label="Snapshot" onPress={() => run('ringer snapshot', refresh)} />
        <Button label="Set Vibrate" onPress={() => set('vibrate')} />
        <Button label="Set Normal" onPress={() => set('normal')} />
        <Button label="Set Silent" onPress={() => set('silent')} />
      </Row>
      <Row>
        <Button label="DND: priority" onPress={() => run('dnd priority', () => HolyRadiusNative.setInterruptionFilter('priority'))} />
        <Button label="DND: off" onPress={() => run('dnd all', () => HolyRadiusNative.setInterruptionFilter('all'))} />
      </Row>
    </Section>
  );
}

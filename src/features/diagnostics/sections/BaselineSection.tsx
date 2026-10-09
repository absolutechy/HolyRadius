import { Text } from 'react-native';

import type { Registry } from '../../../../modules/holy-radius-native';
import { startBaseline, stopBaseline } from '../../taskmanager-baseline/baseline';
import { Button, KV, Row, Section, styles } from '../ui';
import type { Run } from '../useRunner';

type Props = { running: boolean | null; registry: Registry | null; run: Run; refresh: () => Promise<void> };

export function BaselineSection({ running, registry, run, refresh }: Props) {
  const fences = registry?.fences ?? [];
  return (
    <Section title="Expo TaskManager baseline (M10)">
      <KV k="running" v={running ?? 'unknown'} />
      <Text style={styles.note}>
        Registers the same test points via expo-location (ENTER/EXIT only, ids prefixed “tm:”). Events are logged with source
        “taskmanager”. Restart after changing fences.
      </Text>
      <Row>
        <Button
          label={`Start (${fences.length} fences)`}
          disabled={fences.length === 0}
          onPress={() =>
            run('baseline start', async () => {
              await startBaseline(fences);
              await refresh();
            })
          }
        />
        <Button
          label="Stop"
          onPress={() =>
            run('baseline stop', async () => {
              await stopBaseline();
              await refresh();
            })
          }
        />
      </Row>
    </Section>
  );
}

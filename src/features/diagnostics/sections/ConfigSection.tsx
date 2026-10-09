import { useState } from 'react';
import { Text } from 'react-native';

import { HolyRadiusNative, type PrototypeConfig } from '../../../../modules/holy-radius-native';
import { Button, Field, Mono, Row, Section, styles } from '../ui';
import type { Run } from '../useRunner';

export function ConfigSection({ config, run, refresh }: { config: PrototypeConfig | null; run: Run; refresh: () => Promise<void> }) {
  const [patch, setPatch] = useState('');
  const [error, setError] = useState<string | null>(null);

  const apply = () => {
    let parsed: unknown;
    try {
      parsed = JSON.parse(patch);
    } catch {
      setError('Patch must be a JSON object, e.g. {"maxSessionMs": 120000}');
      return;
    }
    if (typeof parsed !== 'object' || parsed === null || Array.isArray(parsed)) {
      setError('Patch must be a JSON object');
      return;
    }
    setError(null);
    run('set config', async () => {
      const r = await HolyRadiusNative.setConfig(parsed as Partial<PrototypeConfig>);
      await refresh();
      return r;
    });
  };

  return (
    <Section title="Prototype config">
      <Mono>{config ? JSON.stringify(config, null, 1) : '…'}</Mono>
      <Field label="JSON patch" value={patch} onChangeText={setPatch} placeholder='{"defaultLoiterMs": 60000}' />
      {error ? <Text style={styles.error}>{error}</Text> : null}
      <Row>
        <Button label="Apply patch" onPress={apply} />
        <Button
          label="Reset defaults"
          onPress={() =>
            run('reset config', async () => {
              const r = await HolyRadiusNative.resetConfig();
              await refresh();
              return r;
            })
          }
        />
      </Row>
    </Section>
  );
}

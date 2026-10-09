import { useCallback, useEffect, useRef, useState } from 'react';
import { AppState, Text } from 'react-native';

import { HolyRadiusNative, type LogEntry } from '../../../../modules/holy-radius-native';
import { LOG_POLL_MS, LOG_VIEW_MAX } from '../../../config/prototype';
import { filterLog, formatEntry, lastId, mergeLog, type LogFilter } from '../logView';
import { Button, Mono, Row, Section, styles } from '../ui';
import type { Run } from '../useRunner';

const FILTERS: LogFilter[] = ['all', 'native', 'taskmanager', 'js'];

export function LogSection({ run }: { run: Run }) {
  const [entries, setEntries] = useState<LogEntry[]>([]);
  const [filter, setFilter] = useState<LogFilter>('all');
  const entriesRef = useRef<LogEntry[]>([]);
  entriesRef.current = entries;

  const poll = useCallback(async () => {
    try {
      const fresh = await HolyRadiusNative.getLog(lastId(entriesRef.current), LOG_VIEW_MAX);
      if (fresh.length > 0) setEntries((cur) => mergeLog(cur, fresh, LOG_VIEW_MAX));
    } catch {
      // keep the last view; the next poll retries
    }
  }, []);

  // Poll only while the app is in the foreground.
  useEffect(() => {
    let timer: ReturnType<typeof setInterval> | null = null;
    const start = () => {
      if (timer == null) {
        void poll();
        timer = setInterval(poll, LOG_POLL_MS);
      }
    };
    const stop = () => {
      if (timer != null) clearInterval(timer);
      timer = null;
    };
    if (AppState.currentState === 'active') start();
    const sub = AppState.addEventListener('change', (s) => (s === 'active' ? start() : stop()));
    return () => {
      stop();
      sub.remove();
    };
  }, [poll]);

  const shown = filterLog(entries, filter).slice().reverse();

  return (
    <Section title="Event log (M1)">
      <Row>
        {FILTERS.map((f) => (
          <Button key={f} label={f === filter ? `• ${f}` : f} onPress={() => setFilter(f)} />
        ))}
      </Row>
      <Row>
        <Button label="Export" onPress={() => run('export log', HolyRadiusNative.exportLog)} />
        <Button
          label="Clear"
          onPress={() =>
            run('clear log', async () => {
              await HolyRadiusNative.clearLog();
              setEntries([]);
            })
          }
        />
      </Row>
      {shown.length === 0 ? <Text style={styles.note}>No entries.</Text> : null}
      {shown.map((e) => (
        <Mono key={e.id}>{formatEntry(e)}</Mono>
      ))}
    </Section>
  );
}

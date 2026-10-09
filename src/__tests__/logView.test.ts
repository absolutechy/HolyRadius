import { parseLogLines, type LogEntry } from '../../modules/holy-radius-native';
import { filterLog, formatEntry, lastId, mergeLog } from '../features/diagnostics/logView';

const e = (id: number, source: LogEntry['source'] = 'native'): LogEntry => ({
  id,
  t: 0,
  et: 0,
  source,
  type: 't',
  proc: 'bg',
  payload: {},
});

describe('log view', () => {
  it('merges, dedups, sorts and caps', () => {
    const merged = mergeLog([e(1), e(3)], [e(2), e(3), e(4)], 3);
    expect(merged.map((x) => x.id)).toEqual([2, 3, 4]);
  });

  it('filters by source and reports last id', () => {
    const all = [e(1), e(2, 'taskmanager')];
    expect(filterLog(all, 'taskmanager').map((x) => x.id)).toEqual([2]);
    expect(filterLog(all, 'all')).toHaveLength(2);
    expect(lastId(all)).toBe(2);
    expect(lastId([])).toBe(0);
  });

  it('formats a compact line with process flags', () => {
    expect(formatEntry({ ...e(1), coldStart: true })).toContain('[native/bg,cold] t {}');
  });

  it('parseLogLines skips corrupt lines', () => {
    expect(parseLogLines([JSON.stringify(e(5)), '{"broken', '']).map((x) => x.id)).toEqual([5]);
  });
});

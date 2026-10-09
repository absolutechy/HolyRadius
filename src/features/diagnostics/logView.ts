import type { LogEntry, LogSource } from '../../../modules/holy-radius-native';

export type LogFilter = LogSource | 'all';

/** Merges newly fetched entries into the view: dedup by id, ascending, capped to the newest `max`. */
export function mergeLog(existing: LogEntry[], incoming: LogEntry[], max: number): LogEntry[] {
  if (incoming.length === 0) return existing;
  const byId = new Map<number, LogEntry>();
  for (const e of existing) byId.set(e.id, e);
  for (const e of incoming) byId.set(e.id, e);
  const merged = [...byId.values()].sort((a, b) => a.id - b.id);
  return merged.length > max ? merged.slice(merged.length - max) : merged;
}

export function filterLog(entries: LogEntry[], filter: LogFilter): LogEntry[] {
  return filter === 'all' ? entries : entries.filter((e) => e.source === filter);
}

export function lastId(entries: LogEntry[]): number {
  return entries.length === 0 ? 0 : entries[entries.length - 1].id;
}

/** One-line summary for the log list. */
export function formatEntry(e: LogEntry): string {
  const time = new Date(e.t).toISOString().slice(11, 19);
  const flags = `${e.proc}${e.coldStart ? ',cold' : ''}`;
  const payload = JSON.stringify(e.payload);
  const short = payload.length > 240 ? `${payload.slice(0, 240)}…` : payload;
  return `${time} [${e.source}/${flags}] ${e.type} ${short}`;
}

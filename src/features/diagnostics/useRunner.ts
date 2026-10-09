import { useCallback, useState } from 'react';

export type LastResult = { label: string; ok: boolean; value: unknown; at: number } | null;

/** Runs an async diagnostics action and keeps its outcome for the "Last result" panel. */
export function useRunner() {
  const [last, setLast] = useState<LastResult>(null);
  const [busy, setBusy] = useState(false);

  const run = useCallback(async <T,>(label: string, fn: () => Promise<T>): Promise<T | undefined> => {
    setBusy(true);
    try {
      const value = await fn();
      setLast({ label, ok: true, value: value ?? 'done', at: Date.now() });
      return value;
    } catch (e) {
      setLast({ label, ok: false, value: e instanceof Error ? e.message : String(e), at: Date.now() });
      return undefined;
    } finally {
      setBusy(false);
    }
  }, []);

  return { last, busy, run };
}

export type Run = ReturnType<typeof useRunner>['run'];

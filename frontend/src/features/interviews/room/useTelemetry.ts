import { useCallback, useEffect, useRef } from 'react';
import { sessionsApi } from '../../../api/endpoints';
import type { TelemetryEvent, TelemetryType } from '../../../api/models';

const FLUSH_INTERVAL_MS = 5_000;
const MAX_BATCH = 50;

/**
 * Integrity signals (pastes, tab switches), batched to POST /sessions/{id}/telemetry. They are shown in the report
 * as context only. Failures are dropped silently: telemetry must never get in the candidate's way.
 */
export function useTelemetry(sessionId: string, enabled: boolean) {
  const queue = useRef<TelemetryEvent[]>([]);

  const flush = useCallback(() => {
    if (queue.current.length === 0) return;
    const events = queue.current.splice(0, MAX_BATCH);
    void sessionsApi.telemetry(sessionId, events).catch(() => undefined);
  }, [sessionId]);

  const record = useCallback(
    (type: TelemetryType, size?: number) => {
      if (!enabled) return;
      queue.current.push({ type, at: new Date().toISOString(), ...(size !== undefined ? { size } : {}) });
      if (queue.current.length >= MAX_BATCH) flush();
    },
    [enabled, flush],
  );

  useEffect(() => {
    if (!enabled) return;
    const onVisibility = () => record(document.visibilityState === 'hidden' ? 'TAB_HIDDEN' : 'TAB_VISIBLE');
    document.addEventListener('visibilitychange', onVisibility);
    const timer = setInterval(flush, FLUSH_INTERVAL_MS);
    return () => {
      document.removeEventListener('visibilitychange', onVisibility);
      clearInterval(timer);
      flush();
    };
  }, [enabled, record, flush]);

  const recordPaste = useCallback((size: number) => record('PASTE', size), [record]);
  return { recordPaste };
}

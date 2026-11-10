import { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { sessionsApi } from '../../../api/endpoints';
import type { SessionDetail } from '../../../api/models';
import { openEventStream, type StreamStatus } from '../../../api/sse';
import { initialRoomState, roomReducer, type RoomAction } from './roomState';

const TERMINAL = new Set(['COMPLETED', 'ABANDONED']);

/**
 * Loads the session, then follows its SSE stream (resuming from the snapshot's lastEventId). `resync` events and
 * reconnects that the server cannot replay trigger a fresh snapshot.
 */
export function useSessionRoom(sessionId: string) {
  const [state, dispatch] = useReducer(roomReducer, initialRoomState);
  const [loadError, setLoadError] = useState<unknown>(null);
  const [connection, setConnection] = useState<StreamStatus>('connecting');
  const statusRef = useRef<string | undefined>(undefined);
  statusRef.current = state.detail?.session.status;

  const reload = useCallback(async (): Promise<SessionDetail | null> => {
    try {
      const detail = await sessionsApi.get(sessionId);
      dispatch({ type: 'loaded', detail });
      setLoadError(null);
      return detail;
    } catch (error) {
      setLoadError(error);
      return null;
    }
  }, [sessionId]);

  const [streamFrom, setStreamFrom] = useState<{ lastEventId: string | undefined } | null>(null);

  useEffect(() => {
    let active = true;
    void reload().then((detail) => {
      if (active && detail && !TERMINAL.has(detail.session.status)) {
        setStreamFrom({ lastEventId: detail.lastEventId });
      }
    });
    return () => {
      active = false;
    };
  }, [reload]);

  useEffect(() => {
    if (!streamFrom) return;
    const stream = openEventStream({
      path: `/api/v1/sessions/${encodeURIComponent(sessionId)}/stream`,
      lastEventId: streamFrom.lastEventId,
      onStatus: setConnection,
      shouldReconnect: () => !TERMINAL.has(statusRef.current ?? ''),
      onMessage: (message) => {
        if (message.event === 'resync') {
          void reload();
          return;
        }
        let data: unknown = {};
        try {
          data = message.data ? JSON.parse(message.data) : {};
        } catch {
          return; // malformed payload: ignore rather than break the room
        }
        dispatch({ type: 'event', event: message.event, data });
      },
    });
    return () => stream.close();
  }, [streamFrom, sessionId, reload]);

  const send = useCallback((action: RoomAction) => dispatch(action), []);

  return { state, dispatch: send, loadError, connection, reload };
}

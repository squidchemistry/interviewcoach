/**
 * Server-Sent Events over `fetch` (EventSource cannot send an Authorization header).
 * {@link SseParser} implements the WHATWG event-stream parsing rules; {@link openEventStream} adds auth,
 * token refresh, resume-from-last-id and exponential reconnect backoff (1 s → 30 s).
 */
import { getSession, refreshSession } from './session';
import { apiUrl } from './url';

export interface SseMessage {
  /** `event:` field; "message" when absent. */
  event: string;
  data: string;
  /** The last event id seen so far (ids persist across messages per the spec). */
  id: string | undefined;
}

export class SseParser {
  private buffer = '';
  private data: string[] = [];
  private eventName = '';
  private sawData = false;
  lastEventId: string | undefined;
  /** Server-suggested reconnection delay (`retry:` field), in ms. */
  retry: number | undefined;

  /** Feeds a decoded chunk; returns every message completed by it. Chunks may split lines anywhere. */
  feed(chunk: string): SseMessage[] {
    this.buffer += chunk;
    const messages: SseMessage[] = [];
    // Lines end with CRLF, LF or CR. A trailing CR may be the first half of a CRLF split across chunks: keep it.
    for (;;) {
      const match = /\r\n|\n|\r/.exec(this.buffer);
      if (!match) break;
      if (match[0] === '\r' && match.index === this.buffer.length - 1) break;
      const line = this.buffer.slice(0, match.index);
      this.buffer = this.buffer.slice(match.index + match[0].length);
      const message = this.processLine(line);
      if (message) messages.push(message);
    }
    return messages;
  }

  private processLine(line: string): SseMessage | null {
    if (line === '') {
      return this.dispatch();
    }
    if (line.startsWith(':')) {
      return null; // comment (heartbeat)
    }
    const colon = line.indexOf(':');
    const field = colon === -1 ? line : line.slice(0, colon);
    let value = colon === -1 ? '' : line.slice(colon + 1);
    if (value.startsWith(' ')) value = value.slice(1);
    switch (field) {
      case 'event':
        this.eventName = value;
        break;
      case 'data':
        this.data.push(value);
        this.sawData = true;
        break;
      case 'id':
        if (!value.includes('\0')) this.lastEventId = value;
        break;
      case 'retry':
        if (/^\d+$/.test(value)) this.retry = Number(value);
        break;
      default:
        break;
    }
    return null;
  }

  private dispatch(): SseMessage | null {
    const message: SseMessage | null = this.sawData
      ? { event: this.eventName || 'message', data: this.data.join('\n'), id: this.lastEventId }
      : null;
    this.data = [];
    this.eventName = '';
    this.sawData = false;
    return message;
  }
}

export type StreamStatus = 'connecting' | 'open' | 'reconnecting' | 'closed';

export interface EventStreamOptions {
  /** API path, e.g. `/api/v1/sessions/{id}/stream`. */
  path: string;
  /** Resume after this id (sent as `?after=` and `Last-Event-ID`). */
  lastEventId?: string | undefined;
  onMessage: (message: SseMessage) => void;
  onStatus?: (status: StreamStatus) => void;
  /** Called when the server ends the stream; return false to stop (e.g. the session is over). Default: reconnect. */
  shouldReconnect?: () => boolean;
  /** Fatal HTTP failure (4xx other than 401): the stream stops. */
  onFatal?: (status: number) => void;
}

const MIN_BACKOFF_MS = 1_000;
const MAX_BACKOFF_MS = 30_000;

export function backoffDelay(attempt: number): number {
  return Math.min(MAX_BACKOFF_MS, MIN_BACKOFF_MS * 2 ** Math.max(0, attempt));
}

/** Opens the stream and keeps it open until the returned `close` is called. */
export function openEventStream(options: EventStreamOptions): { close: () => void } {
  let closed = false;
  let controller: AbortController | null = null;
  let timer: ReturnType<typeof setTimeout> | null = null;
  let lastId = options.lastEventId;
  let attempt = 0;

  const status = (s: StreamStatus) => options.onStatus?.(s);

  const scheduleReconnect = () => {
    if (closed) return;
    status('reconnecting');
    const delay = backoffDelay(attempt);
    attempt += 1;
    timer = setTimeout(() => void connect(), delay);
  };

  async function request(token: string | null): Promise<Response> {
    const url = new URL(apiUrl(options.path));
    if (lastId) url.searchParams.set('after', lastId);
    controller = new AbortController();
    return fetch(url.toString(), {
      credentials: 'include',
      headers: {
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
        ...(lastId ? { 'Last-Event-ID': lastId } : {}),
      },
      cache: 'no-store',
      signal: controller.signal,
    });
  }

  async function connect() {
    if (closed) return;
    status(attempt === 0 ? 'connecting' : 'reconnecting');
    let response: Response;
    try {
      response = await request(getSession().accessToken);
      if (response.status === 401) {
        const refreshed = await refreshSession();
        if (!refreshed) {
          options.onFatal?.(401);
          status('closed');
          return;
        }
        response = await request(refreshed.accessToken);
      }
    } catch {
      scheduleReconnect();
      return;
    }
    if (closed) return;
    if (!response.ok || !response.body) {
      if (response.status >= 400 && response.status < 500 && response.status !== 429) {
        options.onFatal?.(response.status);
        status('closed');
        return;
      }
      scheduleReconnect();
      return;
    }

    status('open');
    const parser = new SseParser();
    parser.lastEventId = lastId;
    const reader = response.body.pipeThrough(new TextDecoderStream()).getReader();
    try {
      for (;;) {
        const { value, done } = await reader.read();
        if (done) break;
        for (const message of parser.feed(value)) {
          attempt = 0; // healthy stream: reset backoff
          lastId = message.id ?? lastId;
          if (!closed) options.onMessage(message);
        }
      }
    } catch {
      // network drop or abort: fall through to reconnect logic
    }
    lastId = parser.lastEventId ?? lastId;
    if (closed) return;
    if (options.shouldReconnect && !options.shouldReconnect()) {
      status('closed');
      return;
    }
    scheduleReconnect();
  }

  void connect();

  return {
    close() {
      closed = true;
      if (timer) clearTimeout(timer);
      controller?.abort();
      status('closed');
    },
  };
}

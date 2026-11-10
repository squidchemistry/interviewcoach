import { describe, expect, it } from 'vitest';
import { backoffDelay, SseParser } from './sse';

describe('SseParser', () => {
  it('parses events split across arbitrary chunk boundaries', () => {
    const parser = new SseParser();
    const stream = 'id: 1-0\nevent: token\ndata: {"turnId":"a","delta":"Hel"}\n\nid: 1-1\nevent: token\ndata: {"turnId":"a","delta":"lo"}\n\n';
    const messages = [];
    for (let i = 0; i < stream.length; i += 3) {
      messages.push(...parser.feed(stream.slice(i, i + 3)));
    }
    expect(messages).toEqual([
      { event: 'token', data: '{"turnId":"a","delta":"Hel"}', id: '1-0' },
      { event: 'token', data: '{"turnId":"a","delta":"lo"}', id: '1-1' },
    ]);
    expect(parser.lastEventId).toBe('1-1');
  });

  it('joins multi-line data with newlines and defaults the event name to "message"', () => {
    const parser = new SseParser();
    expect(parser.feed('data: line one\ndata: line two\ndata\n\n')).toEqual([{ event: 'message', data: 'line one\nline two\n', id: undefined }]);
  });

  it('ignores comments (heartbeats) and blocks without data', () => {
    const parser = new SseParser();
    expect(parser.feed(': ping\n\n: ping\n')).toEqual([]);
    expect(parser.feed('event: resync\n\n')).toEqual([]);
    expect(parser.feed('event: resync\ndata: {}\n\n')).toEqual([{ event: 'resync', data: '{}', id: undefined }]);
  });

  it('handles CRLF and a CR split from its LF across chunks', () => {
    const parser = new SseParser();
    expect(parser.feed('event: state.changed\r')).toEqual([]);
    expect(parser.feed('\ndata: {"status":"IN_PROGRESS"}\r\n\r\n')).toEqual([
      { event: 'state.changed', data: '{"status":"IN_PROGRESS"}', id: undefined },
    ]);
  });

  it('keeps the last id across messages, ignores ids with NUL and parses retry', () => {
    const parser = new SseParser();
    parser.feed('id: 5-0\ndata: a\n\n');
    const [message] = parser.feed('id: bad\0id\nretry: 2500\ndata: b\n\n');
    expect(message).toEqual({ event: 'message', data: 'b', id: '5-0' });
    expect(parser.retry).toBe(2500);
  });

  it('strips only one leading space from values and keeps colons in data', () => {
    const parser = new SseParser();
    expect(parser.feed('data:  {"a":"b:c"}\n\n')[0]?.data).toBe(' {"a":"b:c"}');
  });
});

describe('backoffDelay', () => {
  it('doubles from 1 s and caps at 30 s', () => {
    expect([0, 1, 2, 3, 4, 5, 9].map(backoffDelay)).toEqual([1000, 2000, 4000, 8000, 16000, 30000, 30000]);
  });
});

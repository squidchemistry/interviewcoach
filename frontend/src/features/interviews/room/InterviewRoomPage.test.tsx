import { act, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { setSession } from '../../../api/session';
import type { EventStreamOptions } from '../../../api/sse';
import { makeAuth } from '../../../test/fixtures';
import { makeDetail, SESSION_ID } from '../../../test/interviewFixtures';
import { renderPage } from '../../../test/render';
import { server } from '../../../test/server';
import { InterviewRoomPage } from './InterviewRoomPage';

const streams: EventStreamOptions[] = [];

vi.mock('../../../api/sse', async (importOriginal) => {
  const actual = await importOriginal<Record<string, unknown>>();
  return {
    ...actual,
    openEventStream: (options: EventStreamOptions) => {
      streams.push(options);
      options.onStatus?.('open');
      return { close: () => undefined };
    },
  };
});

function emit(event: string, data: unknown, id = `${Date.now()}-0`) {
  act(() => {
    streams.at(-1)!.onMessage({ event, data: JSON.stringify(data), id });
  });
}

function signedIn() {
  setSession(makeAuth());
  server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth())));
}

describe('InterviewRoomPage', () => {
  beforeEach(() => {
    streams.length = 0;
    signedIn();
  });

  it('renders the question and transcript, resumes the stream from lastEventId, and streams tokens into a live bubble', async () => {
    server.use(http.get(`*/api/v1/sessions/${SESSION_ID}`, () => HttpResponse.json(makeDetail())));
    renderPage(<InterviewRoomPage />, { path: '/interviews/:id', route: `/interviews/${SESSION_ID}` });

    expect(await screen.findByRole('heading', { name: 'Tell me about a conflict' })).toBeInTheDocument();
    expect(screen.getByText('Q1/3')).toBeInTheDocument();
    expect(screen.getByText('Starting at your role level')).toBeInTheDocument();
    const transcript = screen.getByRole('list', { name: 'Transcript' });
    expect(within(transcript).getByText('disagreed')).toBeInTheDocument();

    expect(streams).toHaveLength(1);
    expect(streams[0]!.lastEventId).toBe('1730960000000-1');
    expect(streams[0]!.path).toBe(`/api/v1/sessions/${SESSION_ID}/stream`);

    emit('state.changed', { status: 'IN_PROGRESS', stage: 'FOLLOW_UP', processing: true, currentIndex: 0 });
    emit('turn.started', { turnId: 't-9', kind: 'FOLLOW_UP', role: 'INTERVIEWER' });
    emit('token', { turnId: 't-9', delta: 'What did you ' });
    emit('token', { turnId: 't-9', delta: 'learn?' });

    const bubble = screen.getByTestId('streaming-bubble');
    expect(bubble).toHaveTextContent('What did you learn?');
    expect(bubble.parentElement).toHaveAttribute('aria-live', 'polite');
    expect(screen.getByRole('button', { name: 'Send answer' })).toBeDisabled();

    emit('turn.completed', { turnId: 't-9', seq: 2, role: 'INTERVIEWER', kind: 'FOLLOW_UP', content: 'What did you learn?', format: 'MARKDOWN', sessionQuestionId: 'q-1' });
    // a replayed token for a finished turn must not resurrect the bubble
    emit('token', { turnId: 't-9', delta: 'learn?' });
    emit('state.changed', { status: 'IN_PROGRESS', stage: 'ANSWER', processing: false, currentIndex: 0 });

    expect(screen.queryByTestId('streaming-bubble')).not.toBeInTheDocument();
    expect(within(transcript).getAllByText('What did you learn?')).toHaveLength(1);
  });

  it('keeps Send disabled while the interviewer is processing and submits with Ctrl+Enter once it is done', async () => {
    let body: Record<string, unknown> | null = null;
    server.use(
      http.get(`*/api/v1/sessions/${SESSION_ID}`, () => HttpResponse.json(makeDetail({}, { processing: true }))),
      http.post(`*/api/v1/sessions/${SESSION_ID}/answers`, async ({ request }) => {
        body = (await request.json()) as Record<string, unknown>;
        return HttpResponse.json({ status: 'IN_PROGRESS', stage: 'ANSWER', processing: true }, { status: 202 });
      }),
    );
    renderPage(<InterviewRoomPage />, { path: '/interviews/:id', route: `/interviews/${SESSION_ID}` });

    const box = await screen.findByLabelText('Your answer');
    await userEvent.type(box, 'I listened first and proposed a spike.');
    expect(screen.getByRole('button', { name: 'Send answer' })).toBeDisabled();
    expect(screen.getByText(/Wait for the interviewer to finish/)).toBeInTheDocument();

    emit('state.changed', { status: 'IN_PROGRESS', stage: 'ANSWER', processing: false, currentIndex: 0 });
    expect(screen.getByRole('button', { name: 'Send answer' })).toBeEnabled();

    await userEvent.type(box, '{Control>}{Enter}{/Control}');
    await vi.waitFor(() => expect(body).not.toBeNull());
    expect(body).toMatchObject({ content: 'I listened first and proposed a spike.', inputMode: 'TEXT' });
    expect(typeof body!.clientMessageId).toBe('string');
    expect(await screen.findByLabelText('You, sending')).toBeInTheDocument();
    expect(box).toHaveValue('');
    expect(screen.getByRole('button', { name: 'Send answer' })).toBeDisabled();
  });

  it('shows the stream error with a retry button that calls retry-turn', async () => {
    let retried = false;
    server.use(
      http.get(`*/api/v1/sessions/${SESSION_ID}`, () => HttpResponse.json(makeDetail())),
      http.post(`*/api/v1/sessions/${SESSION_ID}/retry-turn`, () => {
        retried = true;
        return HttpResponse.json({ status: 'IN_PROGRESS', stage: 'FOLLOW_UP', processing: true }, { status: 202 });
      }),
    );
    renderPage(<InterviewRoomPage />, { path: '/interviews/:id', route: `/interviews/${SESSION_ID}` });
    await screen.findByRole('heading', { name: 'Tell me about a conflict' });

    emit('error', { code: 'AI_UNAVAILABLE', message: 'The interviewer is temporarily unavailable.', retryable: true });
    expect(screen.getByRole('alert')).toHaveTextContent('The interviewer is temporarily unavailable.');
    await userEvent.click(screen.getByRole('button', { name: 'Retry' }));
    await vi.waitFor(() => expect(retried).toBe(true));
  });

  it('shows adaptive-difficulty feedback, the evaluating overlay, and leaves for the report when completed', async () => {
    server.use(http.get(`*/api/v1/sessions/${SESSION_ID}`, () => HttpResponse.json(makeDetail())));
    renderPage(<InterviewRoomPage />, { path: '/interviews/:id', route: `/interviews/${SESSION_ID}` });
    await screen.findByRole('heading', { name: 'Tell me about a conflict' });

    emit('assessment', { sessionQuestionId: 'q-1', position: 0, turnScore: 8, nextDifficulty: 'MEDIUM', reason: 'Strong answer' });
    expect(screen.getByRole('status', { name: 'Adaptive difficulty' })).toHaveTextContent('Q1 scored 8/10 → next question Medium. Strong answer');

    emit('state.changed', { status: 'EVALUATING', processing: false, currentIndex: 0 });
    emit('evaluation.progress', { status: 'RUNNING', step: 'Scoring against the rubric' });
    expect(screen.getByRole('dialog', { name: 'Evaluating…' })).toBeInTheDocument();

    emit('state.changed', { status: 'COMPLETED', processing: false, currentIndex: 0 });
    expect(await screen.findByTestId('location')).toHaveTextContent(`/interviews/${SESSION_ID}/report`);
  });
});

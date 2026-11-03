import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import InterviewRunner from './InterviewRunner';
import { sessionsAPI } from '../services/api';

jest.mock('../services/api', () => ({
  sessionsAPI: { get: jest.fn(), submitAnswer: jest.fn() },
}));

const evaluation = { score: 6, strengths: ['clear'], weaknesses: [], improved_answer: 'better' };

const baseSession = {
  id: 's1',
  role: 'Backend Engineer',
  experience: '2-5 years',
  status: 'in_progress',
  created_at: '2026-11-03T10:00:00Z',
  completed_at: null,
  total_questions: 2,
  answered_questions: 1,
  questions: [
    { id: 'q1', position: 0, text: 'First question?', answer: { id: 'a1', text: 'old', created_at: '', evaluation } },
    { id: 'q2', position: 1, text: 'Second question?', answer: null },
  ],
};

function renderRunner() {
  return render(
    <MemoryRouter initialEntries={['/sessions/s1/run']}>
      <Routes>
        <Route path="/sessions/:sessionId/run" element={<InterviewRunner />} />
        <Route path="/sessions/:sessionId" element={<p>Results page</p>} />
      </Routes>
    </MemoryRouter>
  );
}

afterEach(() => jest.resetAllMocks());

test('resumes at the first unanswered question and shows results when done', async () => {
  sessionsAPI.get.mockResolvedValue(baseSession);
  sessionsAPI.submitAnswer.mockResolvedValue({
    session_id: 's1',
    question_id: 'q2',
    session_status: 'completed',
    answered_questions: 2,
    total_questions: 2,
    next_question_id: null,
    answer: { id: 'a2', text: 'My answer', created_at: '', evaluation },
  });

  renderRunner();

  expect(await screen.findByText('Second question?')).toBeInTheDocument();
  expect(screen.getByText('2 of 2')).toBeInTheDocument();

  userEvent.type(screen.getByLabelText('Your Answer'), 'My answer');
  userEvent.click(screen.getByRole('button', { name: /Complete Interview/ }));

  expect(await screen.findByText('Results page')).toBeInTheDocument();
  expect(sessionsAPI.submitAnswer).toHaveBeenCalledWith('s1', 'q2', 'My answer');
});

test('keeps the answer and shows the error when submitting fails', async () => {
  sessionsAPI.get.mockResolvedValue(baseSession);
  sessionsAPI.submitAnswer.mockRejectedValue(new Error('Too many requests, please slow down'));

  renderRunner();
  await screen.findByText('Second question?');

  userEvent.type(screen.getByLabelText('Your Answer'), 'Draft answer');
  userEvent.click(screen.getByRole('button', { name: /Complete Interview/ }));

  expect(await screen.findByRole('alert')).toHaveTextContent('Too many requests');
  expect(screen.getByLabelText('Your Answer')).toHaveValue('Draft answer');
});

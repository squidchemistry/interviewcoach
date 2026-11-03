import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import HistoryPage, { PAGE_SIZE } from './HistoryPage';
import { sessionsAPI } from '../services/api';

jest.mock('../services/api', () => ({
  sessionsAPI: { list: jest.fn() },
}));

const session = (overrides) => ({
  id: 'id-1',
  role: 'Backend Engineer',
  experience: '2-5 years',
  status: 'completed',
  created_at: '2026-11-03T10:00:00Z',
  completed_at: '2026-11-03T10:10:00Z',
  total_questions: 3,
  answered_questions: 3,
  average_score: 7.25,
  ...overrides,
});

function renderAt(path) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/history" element={<HistoryPage />} />
      </Routes>
    </MemoryRouter>
  );
}

afterEach(() => jest.resetAllMocks());

test('lists sessions with status, progress and score', async () => {
  sessionsAPI.list.mockResolvedValue({
    items: [
      session(),
      session({ id: 'id-2', role: 'Data Scientist', status: 'in_progress', answered_questions: 1, average_score: null }),
    ],
    total: 2,
    limit: PAGE_SIZE,
    offset: 0,
  });

  renderAt('/history');

  expect(await screen.findByRole('link', { name: 'Backend Engineer' })).toHaveAttribute('href', '/sessions/id-1');
  expect(screen.getByText('Avg 7.3')).toBeInTheDocument();
  expect(screen.getByText('In progress')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: /Continue/ })).toHaveAttribute('href', '/sessions/id-2/run');
  expect(screen.getByText(/Page 1 of 1/)).toBeInTheDocument();
  expect(sessionsAPI.list).toHaveBeenCalledWith({ limit: PAGE_SIZE, offset: 0 });
});

test('Next requests the following page by offset', async () => {
  sessionsAPI.list.mockResolvedValue({
    items: [session()],
    total: PAGE_SIZE + 1,
    limit: PAGE_SIZE,
    offset: 0,
  });

  renderAt('/history');
  await screen.findByText(/Page 1 of 2/);
  expect(screen.getByRole('button', { name: /Previous/ })).toBeDisabled();

  userEvent.click(screen.getByRole('button', { name: /Next/ }));

  await waitFor(() => expect(sessionsAPI.list).toHaveBeenLastCalledWith({ limit: PAGE_SIZE, offset: PAGE_SIZE }));
});

test('shows an empty state', async () => {
  sessionsAPI.list.mockResolvedValue({ items: [], total: 0, limit: PAGE_SIZE, offset: 0 });
  renderAt('/history');
  expect(await screen.findByText('No sessions yet')).toBeInTheDocument();
});

test('shows API errors', async () => {
  sessionsAPI.list.mockRejectedValue(new Error('Cannot reach the API. Is the backend running?'));
  renderAt('/history');
  expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the API');
});

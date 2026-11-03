import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { AppRoutes } from './App';

test('home page links to the interview and the history page', () => {
  render(
    <MemoryRouter initialEntries={['/']}>
      <AppRoutes />
    </MemoryRouter>
  );

  expect(screen.getByRole('heading', { level: 1, name: /AI Interview Coach/i })).toBeInTheDocument();
  expect(screen.getAllByRole('link', { name: /History/i })[0]).toHaveAttribute('href', '/history');
  expect(screen.getAllByRole('link', { name: /Start Interview/i })[0]).toHaveAttribute('href', '/interview');
});

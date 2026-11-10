import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { makeAuth, makeUser } from '../test/fixtures';
import { renderWithProviders } from '../test/render';
import { server } from '../test/server';
import { AppRoutes } from './App';

function signedIn(user = makeUser()) {
  server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth(user))));
}

describe('AppRoutes', () => {
  it('renders the home page for visitors with landmarks, a skip link and sign-up links', async () => {
    renderWithProviders(<AppRoutes />);

    expect(screen.getByRole('heading', { level: 1, name: /practise software-engineering interviews/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Skip to main content' })).toHaveAttribute('href', '#main');
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getAllByRole('heading', { level: 3 })).toHaveLength(4);
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(await within(nav).findByRole('link', { name: 'Sign up' })).toHaveAttribute('href', '/register');
    expect(within(nav).getByRole('link', { name: 'Log in' })).toHaveAttribute('href', '/login');
    expect(await screen.findByText('All systems operational')).toBeInTheDocument();
  });

  it('restores the session from the refresh cookie and greets the user', async () => {
    signedIn();
    renderWithProviders(<AppRoutes />);
    expect(await screen.findByRole('heading', { name: 'Welcome back, Priya' })).toBeInTheDocument();
    expect(screen.getByText(/Preparing for/)).toHaveTextContent('Preparing for SDE-1 with 0.5 years of experience · targeting Amazon.');
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(within(nav).getAllByRole('link', { name: 'Profile: Priya Sharma' }).length).toBeGreaterThan(0);
    expect(within(nav).queryByRole('link', { name: 'Admin panel' })).not.toBeInTheDocument();
  });

  it('sends visitors to login (and back) from protected pages', async () => {
    renderWithProviders(<AppRoutes />, { route: '/profile' });
    expect(await screen.findByRole('heading', { name: 'Log in' })).toBeInTheDocument();
  });

  it('blocks non-admins from admin pages', async () => {
    signedIn();
    renderWithProviders(<AppRoutes />, { route: '/admin/users' });
    expect(await screen.findByRole('heading', { name: 'Admins only' })).toBeInTheDocument();
  });

  it('shows the admin link and page to admins', async () => {
    signedIn(makeUser({ role: 'ADMIN', name: 'Admin' }));
    server.use(
      http.get('*/api/v1/admin/users', () => HttpResponse.json({ items: [], page: 1, pageSize: 20, total: 0 })),
    );
    renderWithProviders(<AppRoutes />, { route: '/admin/users' });
    // the admin panel is a lazily loaded chunk
    expect(await screen.findByRole('heading', { name: 'Admin panel' }, { timeout: 10_000 })).toBeInTheDocument();
    expect(await screen.findByRole('heading', { name: 'Users' })).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: 'Users' })).toHaveAttribute('aria-selected', 'true');
    expect(within(screen.getByRole('navigation', { name: 'Main' })).getAllByRole('link', { name: 'Admin panel' }).length).toBeGreaterThan(0);
  });

  it('redirects signed-in users away from the login page', async () => {
    signedIn();
    renderWithProviders(<AppRoutes />, { route: '/login' });
    expect(await screen.findByRole('heading', { name: 'Welcome back, Priya' })).toBeInTheDocument();
  });

  it('logs out: revokes the session server-side and returns to the visitor view', async () => {
    signedIn();
    let logoutCalled = false;
    server.use(
      http.post('*/api/v1/auth/logout', () => {
        logoutCalled = true;
        server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json({}, { status: 401 })));
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<AppRoutes />);
    await userEvent.click((await screen.findAllByRole('button', { name: 'Log out' }))[0]!);
    expect(await screen.findByRole('link', { name: 'Create a free account' })).toBeInTheDocument();
    expect(logoutCalled).toBe(true);
  });

  it('deletes the account only after typing DELETE', async () => {
    signedIn();
    let deleted = false;
    server.use(
      http.delete('*/api/v1/me', async ({ request }) => {
        deleted = (await request.json() as { confirm: string }).confirm === 'DELETE';
        return new HttpResponse(null, { status: 204 });
      }),
    );
    renderWithProviders(<AppRoutes />, { route: '/profile' });
    const button = await screen.findByRole('button', { name: 'Delete my account' });
    expect(button).toBeDisabled();
    await userEvent.type(screen.getByLabelText('Type "DELETE" to confirm'), 'DELETE');
    await userEvent.click(button);
    expect(await screen.findByText(/Your account has been deleted/)).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Create a free account' })).toBeInTheDocument();
    expect(deleted).toBe(true);
  });

  it('sends signed-in users without a profile to onboarding before practice pages', async () => {
    signedIn(makeUser({ profile: null }));
    renderWithProviders(<AppRoutes />, { route: '/dashboard' });
    expect(await screen.findByRole('button', { name: 'Save and continue' })).toBeInTheDocument();
  });

  it('renders a 404 page for unknown routes with a way back home', async () => {
    renderWithProviders(<AppRoutes />, { route: '/no/such/page' });
    expect(await screen.findByRole('heading', { level: 1, name: 'Page not found' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to home' })).toHaveAttribute('href', '/');
  });
});

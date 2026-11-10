import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { makeAuth, makeUser, problem } from '../../test/fixtures';
import { renderPage } from '../../test/render';
import { server } from '../../test/server';
import { LoginPage } from './LoginPage';

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup();
  await user.type(await screen.findByLabelText('Email'), email);
  await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Log in' }));
}

describe('LoginPage', () => {
  it('validates required fields before calling the API', async () => {
    renderPage(<LoginPage />, { path: '/login' });
    await userEvent.click(await screen.findByRole('button', { name: 'Log in' }));
    expect(screen.getByText('Enter your email address.')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText('Enter your password.')).toBeInTheDocument();
  });

  it('shows a generic message for wrong credentials', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () => {
        const p = problem(401, 'INVALID_CREDENTIALS', 'Email or password is incorrect');
        return HttpResponse.json(p.body, p.init);
      }),
    );
    renderPage(<LoginPage />, { path: '/login' });
    await fillAndSubmit('priya@example.com', 'wrong-password1');
    expect(await screen.findByRole('alert')).toHaveTextContent('Email or password is incorrect.');
  });

  it('shows the lockout message when rate limited', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () => {
        const p = problem(429, 'RATE_LIMITED', 'Too many failed sign-in attempts. Try again in 15 minutes.');
        return HttpResponse.json(p.body, p.init);
      }),
    );
    renderPage(<LoginPage />, { path: '/login' });
    await fillAndSubmit('priya@example.com', 'wrong-password1');
    expect(await screen.findByRole('alert')).toHaveTextContent('Try again in 15 minutes.');
  });

  it('goes to the requested page after logging in', async () => {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json(makeAuth())));
    renderPage(<LoginPage />, { path: '/login', route: '/login?redirect=%2Fprofile' });
    await fillAndSubmit('priya@example.com', 'Corr3ctPassword');
    expect(await screen.findByTestId('location')).toHaveTextContent('/profile');
  });

  it('sends users without a profile to onboarding first', async () => {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json(makeAuth(makeUser({ profile: null })))));
    renderPage(<LoginPage />, { path: '/login', route: '/login?redirect=%2Fprofile' });
    await fillAndSubmit('priya@example.com', 'Corr3ctPassword');
    expect(await screen.findByTestId('location')).toHaveTextContent('/onboarding?redirect=%2Fprofile');
  });

  it('takes admins straight to the requested page even without a candidate profile', async () => {
    server.use(
      http.post('*/api/v1/auth/login', () =>
        HttpResponse.json(makeAuth(makeUser({ role: 'ADMIN', profile: null }))),
      ),
    );
    renderPage(<LoginPage />, { path: '/login', route: '/login?redirect=%2Fadmin%2Fusers' });
    await fillAndSubmit('admin@aicoach.dev', 'Corr3ctPassword');
    expect(await screen.findByTestId('location')).toHaveTextContent('/admin/users');
  });

  it('ignores off-site redirect targets', async () => {
    server.use(http.post('*/api/v1/auth/login', () => HttpResponse.json(makeAuth())));
    renderPage(<LoginPage />, { path: '/login', route: '/login?redirect=%2F%2Fevil.example.com' });
    await fillAndSubmit('priya@example.com', 'Corr3ctPassword');
    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/$/);
  });

  it('offers Google only when the server has it configured, and reports Google errors', async () => {
    renderPage(<LoginPage />, { path: '/login', route: '/login?error=google_cancelled' });
    expect(await screen.findByRole('alert')).toHaveTextContent('Google sign-in was cancelled.');
    expect(screen.queryByRole('link', { name: 'Continue with Google' })).not.toBeInTheDocument();
  });

  it('links Google sign-in with the redirect preserved', async () => {
    server.use(http.get('*/api/v1/auth/providers', () => HttpResponse.json({ password: true, google: true })));
    renderPage(<LoginPage />, { path: '/login', route: '/login?redirect=%2Fprofile' });
    const link = await screen.findByRole('link', { name: 'Continue with Google' });
    expect(link.getAttribute('href')).toMatch(/\/api\/v1\/auth\/google\?redirect=%2Fprofile$/);
  });
});

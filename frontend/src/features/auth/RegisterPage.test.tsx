import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { makeAuth, makeUser, problem } from '../../test/fixtures';
import { renderPage } from '../../test/render';
import { server } from '../../test/server';
import { RegisterPage } from './RegisterPage';
import { validateRegistration } from './validation';

async function fill(name: string, email: string, password: string) {
  const user = userEvent.setup();
  if (name) await user.type(await screen.findByLabelText('Name'), name);
  if (email) await user.type(screen.getByLabelText('Email'), email);
  if (password) await user.type(screen.getByLabelText('Password'), password);
  await user.click(screen.getByRole('button', { name: 'Create account' }));
}

describe('validateRegistration', () => {
  it('mirrors the server password and email rules', () => {
    expect(validateRegistration({ name: 'A', email: 'a@b.co', password: 'abcdefghi1' })).toEqual({});
    expect(validateRegistration({ name: '', email: 'nope', password: 'short1' })).toEqual({
      name: 'Enter your name.',
      email: 'Enter a valid email address.',
      password: 'Use 10 to 128 characters.',
    });
    expect(validateRegistration({ name: 'A', email: 'a@b.co', password: 'onlyletters' }).password).toBe(
      'Include at least one letter and one number.',
    );
    expect(validateRegistration({ name: 'A', email: 'a@b.co', password: '1234567890' }).password).toBe(
      'Include at least one letter and one number.',
    );
  });
});

describe('RegisterPage', () => {
  it('shows the password rules up front and links errors to fields', async () => {
    renderPage(<RegisterPage />, { path: '/register' });
    const password = await screen.findByLabelText('Password');
    expect(password).toHaveAccessibleDescription('At least 10 characters, including a letter and a number.');
    await fill('Priya', 'priya@example.com', 'short');
    expect(password).toHaveAttribute('aria-invalid', 'true');
    expect(password).toHaveAccessibleDescription(/Use 10 to 128 characters/);
  });

  it('shows a field error when the email is taken', async () => {
    server.use(
      http.post('*/api/v1/auth/register', () => {
        const p = problem(409, 'EMAIL_TAKEN', 'An account with this email already exists');
        return HttpResponse.json(p.body, p.init);
      }),
    );
    renderPage(<RegisterPage />, { path: '/register' });
    await fill('Priya', 'priya@example.com', 'Sup3rSecretPass');
    expect(await screen.findByText(/already exists\. Try logging in instead\./)).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
  });

  it('maps server-side validation errors to fields', async () => {
    server.use(
      http.post('*/api/v1/auth/register', () => {
        const p = problem(400, 'VALIDATION_ERROR', 'One or more fields are invalid', [
          { path: 'body.name', message: 'size must be between 0 and 80' },
        ]);
        return HttpResponse.json(p.body, p.init);
      }),
    );
    renderPage(<RegisterPage />, { path: '/register' });
    await fill('Priya', 'priya@example.com', 'Sup3rSecretPass');
    expect(await screen.findByText('size must be between 0 and 80')).toBeInTheDocument();
  });

  it('signs the new user in and starts onboarding', async () => {
    let body: unknown;
    server.use(
      http.post('*/api/v1/auth/register', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json(makeAuth(makeUser({ profile: null })), { status: 201 });
      }),
    );
    renderPage(<RegisterPage />, { path: '/register' });
    await fill('  Priya  ', ' priya@example.com ', 'Sup3rSecretPass');
    expect(await screen.findByTestId('location')).toHaveTextContent('/onboarding');
    expect(body).toEqual({ name: 'Priya', email: 'priya@example.com', password: 'Sup3rSecretPass' });
  });
});

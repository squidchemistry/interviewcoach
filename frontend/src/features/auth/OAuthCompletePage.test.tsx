import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { describe, expect, it } from 'vitest';
import { makeAuth, makeUser } from '../../test/fixtures';
import { renderPage } from '../../test/render';
import { server } from '../../test/server';
import { OAuthCompletePage } from './OAuthCompletePage';

describe('OAuthCompletePage', () => {
  it('exchanges the cookie for a session and continues to the requested page', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth())));
    renderPage(<OAuthCompletePage />, { path: '/auth/complete', route: '/auth/complete?redirect=%2Fprofile' });
    expect(await screen.findByTestId('location')).toHaveTextContent('/profile');
  });

  it('sends brand-new Google users to onboarding', async () => {
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth(makeUser({ profile: null })))));
    renderPage(<OAuthCompletePage />, { path: '/auth/complete', route: '/auth/complete?redirect=%2F' });
    expect(await screen.findByTestId('location')).toHaveTextContent(/^\/onboarding$/);
  });

  it('returns to login with an error when there is no session', async () => {
    renderPage(<OAuthCompletePage />, { path: '/auth/complete' });
    expect(await screen.findByTestId('location')).toHaveTextContent('/login?error=google_failed');
  });
});

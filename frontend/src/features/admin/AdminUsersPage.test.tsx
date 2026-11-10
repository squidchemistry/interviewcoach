import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import { setSession } from '../../api/session';
import { adminRows, makeAuth, makeUser } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { AdminUsersPage } from './AdminUsersPage';

describe('AdminUsersPage', () => {
  it('lists users, searches, and changes roles (never your own)', async () => {
    const admin = makeUser({ role: 'ADMIN', name: 'Admin' });
    setSession(makeAuth(admin));
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth(admin))));
    const searches: string[] = [];
    let patched: { id: string; role: unknown } | null = null;
    server.use(
      http.get('*/api/v1/admin/users', ({ request }) => {
        searches.push(new URL(request.url).search);
        return HttpResponse.json({ items: adminRows, page: 1, pageSize: 20, total: 2 });
      }),
      http.patch('*/api/v1/admin/users/:id/role', async ({ params, request }) => {
        patched = { id: String(params.id), role: ((await request.json()) as { role: string }).role };
        return HttpResponse.json({ ...adminRows[1], role: 'ADMIN' });
      }),
    );
    renderWithProviders(<AdminUsersPage />);

    const table = await screen.findByRole('table', { name: 'Users, newest first' });
    const rows = within(table).getAllByRole('row');
    expect(rows).toHaveLength(3);
    expect(within(rows[1]!).getByText('You')).toBeInTheDocument();
    expect(screen.getByText('Page 1 of 1 · 2 users')).toBeInTheDocument();

    await userEvent.click(within(rows[2]!).getByRole('button', { name: 'Make admin: Rahul' }));
    await vi.waitFor(() => expect(patched).toEqual({ id: adminRows[1]!.id, role: 'ADMIN' }));

    await userEvent.type(screen.getByLabelText('Search by name or email'), 'rahul');
    await userEvent.selectOptions(screen.getByLabelText('Role'), 'USER');
    await userEvent.click(screen.getByRole('button', { name: 'Search' }));
    await vi.waitFor(() => expect(searches.at(-1)).toBe('?page=1&pageSize=20&q=rahul&role=USER'));
  });
});

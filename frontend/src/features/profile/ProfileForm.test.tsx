import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { describe, expect, it, vi } from 'vitest';
import type { CompanyResponse } from '../../api/types';
import { companies, problem, profile } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { MAX_COMPANIES, ProfileForm } from './ProfileForm';

describe('ProfileForm', () => {
  it('prefills an existing profile and submits the edited values', async () => {
    let sent: unknown;
    server.use(
      http.put('*/api/v1/me/profile', async ({ request }) => {
        sent = await request.json();
        return HttpResponse.json({ ...profile, targetRole: 'SDE_2' });
      }),
    );
    const onSaved = vi.fn();
    renderWithProviders(<ProfileForm initial={profile} submitLabel="Save changes" onSaved={onSaved} />);
    const user = userEvent.setup();

    expect(screen.getByRole('radio', { name: /SDE-1/ })).toBeChecked();
    expect(await screen.findByRole('checkbox', { name: 'Amazon' })).toBeChecked();

    await user.click(screen.getByRole('radio', { name: /SDE-2/ }));
    await user.clear(screen.getByLabelText('Years of experience'));
    await user.type(screen.getByLabelText('Years of experience'), '2.5');
    await user.selectOptions(screen.getByLabelText('Preferred coding language'), 'JAVA');
    await user.click(screen.getByRole('checkbox', { name: 'Google' }));
    await user.click(screen.getByRole('button', { name: 'Save changes' }));

    await vi.waitFor(() => expect(onSaved).toHaveBeenCalled());
    expect(sent).toEqual({
      targetRole: 'SDE_2',
      experienceYears: 2.5,
      preferredLanguage: 'JAVA',
      targetCompanyIds: [companies[0]!.id, companies[2]!.id],
    });
  });

  it('rejects experience that is not a multiple of 0.5 without calling the API', async () => {
    const onSaved = vi.fn();
    renderWithProviders(<ProfileForm initial={null} submitLabel="Save" onSaved={onSaved} />);
    const user = userEvent.setup();
    await user.clear(screen.getByLabelText('Years of experience'));
    await user.type(screen.getByLabelText('Years of experience'), '1.3');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('Use steps of 0.5 (for example 1.5).')).toBeInTheDocument();
    expect(screen.getByLabelText('Years of experience')).toHaveAttribute('aria-invalid', 'true');
    expect(onSaved).not.toHaveBeenCalled();
  });

  it('caps target companies at ten', async () => {
    const many: CompanyResponse[] = Array.from({ length: 12 }, (_, i) => ({
      id: `00000000-0000-0000-0000-0000000000${String(i).padStart(2, '0')}`,
      name: `Company ${i}`,
      slug: `company-${i}`,
    }));
    server.use(http.get('*/api/v1/companies', () => HttpResponse.json(many)));
    renderWithProviders(<ProfileForm initial={null} submitLabel="Save" onSaved={vi.fn()} />);
    const user = userEvent.setup();
    const group = await screen.findByRole('group', { name: /Target companies/ });
    await screen.findByRole('checkbox', { name: 'Company 0' });
    for (let i = 0; i < MAX_COMPANIES; i++) {
      await user.click(within(group).getByRole('checkbox', { name: `Company ${i}` }));
    }
    expect(screen.getByText('10 of 10 selected')).toBeInTheDocument();
    expect(within(group).getByRole('checkbox', { name: 'Company 11' })).toBeDisabled();
    expect(within(group).getByRole('checkbox', { name: 'Company 0' })).toBeEnabled();
  });

  it('shows server validation errors', async () => {
    server.use(
      http.put('*/api/v1/me/profile', () => {
        const p = problem(400, 'VALIDATION_ERROR', 'One or more fields are invalid', [
          { path: 'body.targetCompanyIds', message: 'contains an unknown company id' },
        ]);
        return HttpResponse.json(p.body, p.init);
      }),
    );
    renderWithProviders(<ProfileForm initial={profile} submitLabel="Save" onSaved={vi.fn()} />);
    await screen.findByRole('checkbox', { name: 'Amazon' });
    await userEvent.click(screen.getByRole('button', { name: 'Save' }));
    expect(await screen.findByText('contains an unknown company id')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Please fix the highlighted fields.');
  });
});

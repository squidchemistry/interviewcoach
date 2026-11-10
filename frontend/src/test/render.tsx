import { QueryClient } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import { AppProviders } from '../app/App';

/** Renders UI with a fresh, retry-free QueryClient, the auth provider and an in-memory router. */
export function renderWithProviders(ui: ReactElement, { route = '/' }: { route?: string } = {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, gcTime: 0 } } });
  return render(
    <AppProviders client={client}>
      <MemoryRouter initialEntries={[route]}>{ui}</MemoryRouter>
    </AppProviders>,
  );
}

/** Renders the current location so tests can assert where navigation ended up. */
export function LocationProbe() {
  const location = useLocation();
  return <output data-testid="location">{location.pathname + location.search}</output>;
}

/** Renders `ui` at `path` plus a catch-all probe route, for testing navigation away from a page. */
export function renderPage(ui: ReactElement, { path, route }: { path: string; route?: string }) {
  return renderWithProviders(
    <Routes>
      <Route path={path} element={ui} />
      <Route path="*" element={<LocationProbe />} />
    </Routes>,
    { route: route ?? path },
  );
}

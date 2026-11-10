import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { PageSpinner } from '../../components/ui/PageSpinner';
import { StatusPage } from '../../components/ui/StatusPage';
import { useAuth } from './authContext';

/** Renders children only for signed-in users; otherwise sends them to /login and back afterwards. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') {
    return <PageSpinner label="Checking your session" />;
  }
  if (status === 'anonymous') {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }
  return <>{children}</>;
}

/**
 * Signed-in candidates who have not completed onboarding are sent there first (and back afterwards). Admins are
 * never intercepted, matching {@link postLoginPath}.
 */
export function RequireOnboarded({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const location = useLocation();
  return (
    <RequireAuth>
      {user && !user.profile && user.role === 'USER' ? (
        <Navigate to={`/onboarding?redirect=${encodeURIComponent(location.pathname + location.search)}`} replace />
      ) : (
        children
      )}
    </RequireAuth>
  );
}

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  return (
    <RequireAuth>
      {user?.role === 'ADMIN' ? (
        children
      ) : (
        <StatusPage code="403" title="Admins only" message="You don't have permission to view this page." color="bg-nb-orange" />
      )}
    </RequireAuth>
  );
}

/** Login / register pages: signed-in users go home instead. */
export function GuestOnly({ children }: { children: ReactNode }) {
  const { status } = useAuth();
  if (status === 'loading') {
    return <PageSpinner label="Checking your session" />;
  }
  return status === 'authenticated' ? <Navigate to="/" replace /> : <>{children}</>;
}

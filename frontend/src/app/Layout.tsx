import { useEffect, useState } from 'react';
import { Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { buttonClass } from '../components/ui/styles';
import { useAuth } from '../features/auth/authContext';

const APP_LINKS = [
  { to: '/interviews/new', label: 'Practice' },
  { to: '/interviews', label: 'History', end: true },
  { to: '/dashboard', label: 'Dashboard' },
  { to: '/questions', label: 'Questions' },
  { to: '/resume', label: 'Resume' },
  { to: '/revision', label: 'Revision' },
] as const;

const navLinkClass = ({ isActive }: { isActive: boolean }) =>
  `block border-2 px-2.5 py-1 text-sm font-bold ${
    isActive ? 'border-ink bg-nb-yellow text-[#0a0a0a]' : 'border-transparent hover:border-ink'
  }`;

function Logo() {
  return (
    <Link to="/" className="flex items-center gap-2 font-bold">
      <span aria-hidden="true" className="grid h-9 w-9 place-items-center border-3 border-ink bg-nb-yellow font-mono text-lg text-[#0a0a0a] shadow-[3px_3px_0_0_var(--shadow)]">
        AI
      </span>
      <span className="text-lg tracking-tight">Interview Coach</span>
    </Link>
  );
}

function AccountControls({ onNavigate }: { onNavigate: () => void }) {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [loggingOut, setLoggingOut] = useState(false);
  if (!user) return null;
  return (
    <div className="flex flex-wrap items-center gap-2">
      {user.role === 'ADMIN' && (
        <NavLink to="/admin" className={navLinkClass} onClick={onNavigate}>
          Admin panel
        </NavLink>
      )}
      <Link
        to="/billing"
        onClick={onNavigate}
        className={`nb-badge ${user.plan === 'PRO' ? 'bg-nb-violet' : 'bg-nb-green'}`}
        aria-label={`Plan: ${user.plan === 'PRO' ? 'Pro' : 'Free'}`}
      >
        {user.plan === 'PRO' ? '★ Pro' : 'Free'}
      </Link>
      <NavLink to="/profile" className={navLinkClass} onClick={onNavigate}>
        <span className="sr-only">Profile: </span>
        {user.name}
      </NavLink>
      <button
        type="button"
        disabled={loggingOut}
        onClick={async () => {
          setLoggingOut(true);
          try {
            await logout();
          } finally {
            setLoggingOut(false);
            onNavigate();
            navigate('/', { replace: true });
          }
        }}
        className={`${buttonClass.secondary} nb-btn-sm`}
      >
        {loggingOut ? 'Logging out…' : 'Log out'}
      </button>
    </div>
  );
}

function Header() {
  const { status, user } = useAuth();
  const [open, setOpen] = useState(false);
  const location = useLocation();
  useEffect(() => setOpen(false), [location.pathname]);
  const close = () => setOpen(false);

  return (
    <header className="border-b-3 border-ink bg-surface">
      <nav aria-label="Main" className="mx-auto max-w-6xl px-4 py-3 sm:px-6">
        <div className="flex items-center justify-between gap-3">
          <Logo />
          {status !== 'loading' && !user && (
            <div className="flex items-center gap-2">
              <NavLink to="/login" className={navLinkClass}>
                Log in
              </NavLink>
              <Link to="/register" className={`${buttonClass.primary} nb-btn-sm`}>
                Sign up
              </Link>
            </div>
          )}
          {user && (
            <button
              type="button"
              className={`${buttonClass.secondary} nb-btn-sm lg:hidden`}
              aria-expanded={open}
              aria-controls="app-menu"
              onClick={() => setOpen((v) => !v)}
            >
              {open ? 'Close' : 'Menu'}
            </button>
          )}
          {user && (
            <div className="hidden lg:block">
              <AccountControls onNavigate={close} />
            </div>
          )}
        </div>
        {user && (
          <div id="app-menu" className={`${open ? 'block' : 'hidden'} mt-3 lg:mt-3 lg:block`}>
            <ul className="flex flex-col gap-1 border-t-2 border-ink pt-3 lg:flex-row lg:flex-wrap lg:gap-2">
              {APP_LINKS.map((link) => (
                <li key={link.to}>
                  <NavLink to={link.to} end={'end' in link} className={navLinkClass} onClick={close}>
                    {link.label}
                  </NavLink>
                </li>
              ))}
            </ul>
            <div className="mt-3 border-t-2 border-ink pt-3 lg:hidden">
              <AccountControls onNavigate={close} />
            </div>
          </div>
        )}
      </nav>
    </header>
  );
}

export function Layout() {
  return (
    <div className="flex min-h-screen flex-col">
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:fixed focus:left-4 focus:top-4 focus:z-50 focus:border-3 focus:border-ink focus:bg-nb-yellow focus:px-4 focus:py-2 focus:font-bold focus:text-[#0a0a0a]"
      >
        Skip to main content
      </a>
      <Header />
      <main id="main" tabIndex={-1} className="flex-1 focus:outline-none">
        <Outlet />
      </main>
      <footer className="border-t-3 border-ink bg-ink py-5 text-center font-mono text-xs text-paper">
        AI INTERVIEW COACH · PRACTICE TOOL, NOT A HIRING ASSESSMENT
      </footer>
    </div>
  );
}

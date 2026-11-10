import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { authApi, googleSignInUrl } from '../../api/endpoints';
import { Alert, TextField } from '../../components/ui/form';
import { buttonClass } from '../../components/ui/styles';
import { AuthCard } from './AuthCard';
import { postLoginPath, safeRedirect, useAuth } from './authContext';
import { GOOGLE_ERRORS, describeError } from './errorMessages';

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const redirect = safeRedirect(params.get('redirect'));
  const googleError = GOOGLE_ERRORS[params.get('error') ?? ''];

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<{ email?: string; password?: string }>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const providers = useQuery({ queryKey: ['auth', 'providers'], queryFn: authApi.providers, staleTime: Infinity });

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const next: typeof errors = {};
    if (!email.trim()) next.email = 'Enter your email address.';
    if (!password) next.password = 'Enter your password.';
    setErrors(next);
    setFormError(null);
    if (Object.keys(next).length > 0) return;

    setSubmitting(true);
    try {
      const user = await login({ email: email.trim(), password });
      navigate(postLoginPath(user, redirect), { replace: true });
    } catch (error) {
      setFormError(describeError(error));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard title="Log in" subtitle="Welcome back. Pick up where you left off.">
      {googleError && <Alert>{googleError}</Alert>}
      {formError && <Alert>{formError}</Alert>}
      <form noValidate onSubmit={onSubmit} className="mt-4 space-y-4">
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={errors.email}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
        />
        <button type="submit" disabled={submitting} className={`${buttonClass.primary} w-full`}>
          {submitting ? 'Logging in…' : 'Log in'}
        </button>
      </form>

      {providers.data?.google && (
        <>
          <div className="my-5 flex items-center gap-3 font-mono text-xs font-bold" aria-hidden="true">
            <span className="h-[3px] flex-1 bg-ink" />
            or
            <span className="h-[3px] flex-1 bg-ink" />
          </div>
          <a href={googleSignInUrl(redirect)} className={`${buttonClass.secondary} w-full`}>
            Continue with Google
          </a>
        </>
      )}

      <p className="mt-6 text-center text-sm font-medium">
        New here?{' '}
        <Link
          to={redirect === '/' ? '/register' : `/register?redirect=${encodeURIComponent(redirect)}`}
          className="nb-link"
        >
          Create an account
        </Link>
      </p>
    </AuthCard>
  );
}

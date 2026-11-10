import { useState, type FormEvent } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../api/http';
import { Alert, TextField } from '../../components/ui/form';
import { buttonClass } from '../../components/ui/styles';
import { AuthCard } from './AuthCard';
import { postLoginPath, safeRedirect, useAuth } from './authContext';
import { describeError } from './errorMessages';
import { validateRegistration, type Field } from './validation';


export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const redirect = safeRedirect(params.get('redirect'));

  const [values, setValues] = useState<Record<Field, string>>({ name: '', email: '', password: '' });
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const set = (field: Field) => (event: React.ChangeEvent<HTMLInputElement>) =>
    setValues((v) => ({ ...v, [field]: event.target.value }));

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const clientErrors = validateRegistration(values);
    setErrors(clientErrors);
    setFormError(null);
    if (Object.keys(clientErrors).length > 0) return;

    setSubmitting(true);
    try {
      const user = await register({ name: values.name.trim(), email: values.email.trim(), password: values.password });
      navigate(postLoginPath(user, redirect), { replace: true });
    } catch (error) {
      if (error instanceof ApiError && error.code === 'EMAIL_TAKEN') {
        setErrors({ email: 'An account with this email already exists. Try logging in instead.' });
      } else if (error instanceof ApiError && error.code === 'VALIDATION_ERROR') {
        setErrors(error.fieldErrors() as Partial<Record<Field, string>>);
      } else {
        setFormError(describeError(error));
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthCard title="Create your account" subtitle="Free mock interviews with rubric-based feedback.">
      {formError && <Alert>{formError}</Alert>}
      <form noValidate onSubmit={onSubmit} className="mt-4 space-y-4">
        <TextField label="Name" autoComplete="name" value={values.name} onChange={set('name')} error={errors.name} />
        <TextField
          label="Email"
          type="email"
          autoComplete="email"
          value={values.email}
          onChange={set('email')}
          error={errors.email}
        />
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          hint="At least 10 characters, including a letter and a number."
          value={values.password}
          onChange={set('password')}
          error={errors.password}
        />
        <button type="submit" disabled={submitting} className={`${buttonClass.primary} w-full`}>
          {submitting ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="mt-6 text-center text-sm font-medium">
        Already have an account?{' '}
        <Link
          to={redirect === '/' ? '/login' : `/login?redirect=${encodeURIComponent(redirect)}`}
          className="nb-link"
        >
          Log in
        </Link>
      </p>
    </AuthCard>
  );
}

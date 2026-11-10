import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { meApi } from '../../api/endpoints';
import { PageHeader } from '../../components/ui/PageHeader';
import { Alert, TextField } from '../../components/ui/form';
import { buttonClass } from '../../components/ui/styles';
import { useAuth } from '../auth/authContext';
import { describeError } from '../auth/errorMessages';
import { ProfileForm } from './ProfileForm';

export function ProfilePage() {
  const { user, setUser } = useAuth();
  const navigate = useNavigate();
  const [saved, setSaved] = useState(false);
  const [confirm, setConfirm] = useState('');
  const [deleting, setDeleting] = useState(false);
  const [deleteError, setDeleteError] = useState<string | null>(null);

  if (!user) return null;

  async function deleteAccount() {
    setDeleting(true);
    setDeleteError(null);
    try {
      await meApi.deleteAccount();
      // The home page clears the session once we have left this protected page; clearing it here would make
      // RequireAuth bounce the (now anonymous) visitor to the login page before the navigation lands.
      navigate('/', { replace: true, state: { accountDeleted: true } });
    } catch (error) {
      setDeleteError(describeError(error));
      setDeleting(false);
    }
  }

  return (
    <div className="mx-auto max-w-3xl space-y-10 px-4 py-10 sm:px-6">
      <PageHeader title="Your profile" kicker={user.plan === 'PRO' ? '★ Pro plan' : 'Free plan'} kickerColor={user.plan === 'PRO' ? 'bg-nb-violet' : 'bg-nb-green'} />

      <dl className="nb-card grid gap-x-6 gap-y-2 p-5 text-sm sm:grid-cols-[max-content_1fr]">
        <dt className="font-mono text-xs font-bold uppercase">Name</dt>
        <dd className="font-medium">{user.name}</dd>
        <dt className="font-mono text-xs font-bold uppercase">Email</dt>
        <dd className="break-all font-medium">{user.email}</dd>
        <dt className="font-mono text-xs font-bold uppercase">Sign-in</dt>
        <dd className="font-medium">{user.hasPassword ? 'Email and password' : 'Google'}</dd>
      </dl>

      <section aria-labelledby="goals-heading" className="nb-card p-6">
        <h2 id="goals-heading" className="nb-heading mb-5 text-2xl">
          Interview goals
        </h2>
        {saved && (
          <div className="mb-5">
            <Alert tone="success">Profile saved.</Alert>
          </div>
        )}
        <ProfileForm
          initial={user.profile}
          submitLabel="Save changes"
          onSaved={(profile) => {
            setUser({ ...user, profile });
            setSaved(true);
          }}
        />
      </section>

      <section aria-labelledby="danger-heading" className="nb-card border-nb-red p-6" style={{ boxShadow: '6px 6px 0 0 var(--red)' }}>
        <h2 id="danger-heading" className="nb-heading text-2xl">
          <span className="nb-mark bg-nb-red">Delete account</span>
        </h2>
        <p className="mt-3 font-medium">This erases your personal data and signs you out everywhere. It cannot be undone.</p>
        {deleteError && (
          <div className="mt-4">
            <Alert>{deleteError}</Alert>
          </div>
        )}
        <div className="mt-5 flex flex-col gap-4 sm:flex-row sm:items-end">
          <TextField
            label='Type "DELETE" to confirm'
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            autoComplete="off"
            className="sm:w-64"
          />
          <button
            type="button"
            className={buttonClass.danger}
            disabled={confirm !== 'DELETE' || deleting}
            onClick={() => void deleteAccount()}
          >
            {deleting ? 'Deleting…' : 'Delete my account'}
          </button>
        </div>
      </section>
    </div>
  );
}

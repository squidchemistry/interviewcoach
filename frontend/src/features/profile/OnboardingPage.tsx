import { useNavigate, useSearchParams } from 'react-router-dom';
import { PageHeader } from '../../components/ui/PageHeader';
import { safeRedirect, useAuth } from '../auth/authContext';
import { ProfileForm } from './ProfileForm';

export function OnboardingPage() {
  const { user, setUser } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();

  return (
    <div className="mx-auto max-w-3xl px-4 py-10 sm:px-6">
      <PageHeader
        kicker="Step 1 of 1"
        title="Set up your interview profile"
        description="This tunes question difficulty and feedback to the role you're preparing for. You can change it any time."
      />
      <div className="nb-card mt-8 p-6">
        <ProfileForm
          initial={user?.profile}
          submitLabel="Save and continue"
          onSaved={(profile) => {
            if (user) setUser({ ...user, profile });
            navigate(safeRedirect(params.get('redirect')), { replace: true });
          }}
        />
      </div>
    </div>
  );
}

import { useEffect } from 'react';
import { Link, useLocation } from 'react-router-dom';
import { clearSession } from '../../api/session';
import type { UserResponse } from '../../api/types';
import { Alert } from '../../components/ui/form';
import { buttonClass } from '../../components/ui/styles';
import { useAuth } from '../auth/authContext';
import { HealthCard } from '../health/HealthCard';

const MODES = [
  {
    name: 'DSA coding round',
    description: 'Solve a problem in a real editor, run it against test cases, and explain your complexity.',
    color: 'bg-nb-yellow',
    tag: 'CODE',
  },
  {
    name: 'Low-level design',
    description: 'Design classes for systems like a parking lot and defend them under follow-up questions.',
    color: 'bg-nb-blue',
    tag: 'DESIGN',
  },
  {
    name: 'Behavioural (STAR)',
    description: 'Tell stories that are scored separately on Situation, Task, Action and Result.',
    color: 'bg-nb-pink',
    tag: 'STORY',
  },
  {
    name: 'Resume deep-dive',
    description: 'Get questioned on the projects in your own resume, the way real interviewers do.',
    color: 'bg-nb-green',
    tag: 'YOU',
  },
] as const;

const ROLE_LABEL = { SDE_1: 'SDE-1', SDE_2: 'SDE-2' } as const;

function WelcomeCard({ user }: { user: UserResponse }) {
  const profile = user.profile;
  return (
    <section aria-labelledby="welcome-heading" className="nb-accent bg-nb-violet p-6">
      <h2 id="welcome-heading" className="nb-heading text-2xl">
        Welcome back, {user.name.split(' ')[0]}
      </h2>
      {profile ? (
        <p className="mt-2 font-medium">
          Preparing for <strong>{ROLE_LABEL[profile.targetRole]}</strong> with {profile.experienceYears}{' '}
          {Number(profile.experienceYears) === 1 ? 'year' : 'years'} of experience
          {profile.targetCompanies.length > 0 && <> · targeting {profile.targetCompanies.map((c) => c.name).join(', ')}</>}.
        </p>
      ) : (
        <p className="mt-2 font-medium">Tell us what you're preparing for to get tailored questions.</p>
      )}
      <div className="mt-5 flex flex-wrap gap-3">
        <Link to="/interviews/new" className={buttonClass.ink}>
          Start an interview →
        </Link>
        <Link to={profile ? '/profile' : '/onboarding'} className={buttonClass.secondary}>
          {profile ? 'Edit profile' : 'Complete your profile'}
        </Link>
      </div>
    </section>
  );
}

export function HomePage() {
  const { user, status } = useAuth();
  const location = useLocation();
  const accountDeleted = (location.state as { accountDeleted?: boolean } | null)?.accountDeleted === true;

  useEffect(() => {
    if (accountDeleted) clearSession();
  }, [accountDeleted]);

  return (
    <div className="mx-auto flex max-w-6xl flex-col gap-12 px-4 py-10 sm:px-6 sm:py-14">
      {accountDeleted && <Alert tone="success">Your account has been deleted. We're sorry to see you go.</Alert>}

      <section aria-labelledby="hero-heading" className="grid items-center gap-10 lg:grid-cols-[1.4fr_1fr]">
        <div>
          <p className="nb-badge mb-5 bg-nb-green">Mock interviews · AI interviewer · Real feedback</p>
          <h1 id="hero-heading" className="nb-heading text-4xl sm:text-6xl">
            Practise software-engineering interviews with an <span className="nb-mark">AI interviewer</span>
          </h1>
          <p className="mt-6 max-w-2xl text-lg font-medium text-muted">
            Realistic mock interviews that adapt to your answers, followed by rubric-based feedback with evidence from
            your own words and three concrete next steps.
          </p>
          {status === 'anonymous' && (
            <div className="mt-8 flex flex-wrap gap-4">
              <Link to="/register" className={buttonClass.primary}>
                Create a free account
              </Link>
              <Link to="/login" className={buttonClass.secondary}>
                Log in
              </Link>
            </div>
          )}
        </div>
        <div aria-hidden="true" className="relative hidden h-72 lg:block">
          <div className="nb-accent absolute left-6 top-2 w-64 rotate-[-4deg] bg-nb-pink p-4 font-mono text-sm">
            &gt; Walk me through your approach.
          </div>
          <div className="nb-card absolute right-0 top-24 w-64 rotate-[3deg] p-4 font-mono text-sm">
            Two pointers. O(n) time, O(1) space.
          </div>
          <div className="nb-accent absolute bottom-0 left-12 w-56 rotate-[-2deg] bg-nb-yellow p-4 font-bold">
            Problem solving 8.5 / 10
          </div>
        </div>
      </section>

      {user && <WelcomeCard user={user} />}

      <section aria-labelledby="modes-heading">
        <h2 id="modes-heading" className="nb-heading text-3xl">
          Interview rounds
        </h2>
        <ul className="mt-6 grid gap-6 sm:grid-cols-2">
          {MODES.map((mode) => (
            <li key={mode.name} className="nb-card p-5">
              <span className={`nb-badge ${mode.color}`}>{mode.tag}</span>
              <h3 className="nb-heading mt-3 text-xl">{mode.name}</h3>
              <p className="mt-2 font-medium text-muted">{mode.description}</p>
            </li>
          ))}
        </ul>
      </section>

      <HealthCard />
    </div>
  );
}

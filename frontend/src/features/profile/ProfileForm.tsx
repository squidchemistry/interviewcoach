import { useQuery } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { catalogueApi, meApi } from '../../api/endpoints';
import { ApiError } from '../../api/http';
import type { CodeLanguage, ProfileResponse, TargetRole } from '../../api/types';
import { Alert } from '../../components/ui/form';
import { buttonClass } from '../../components/ui/styles';
import { describeError } from '../auth/errorMessages';

export const MAX_COMPANIES = 10;

const ROLES: Array<{ value: TargetRole; label: string; hint: string }> = [
  { value: 'SDE_1', label: 'SDE-1', hint: 'New graduate or up to ~2 years' },
  { value: 'SDE_2', label: 'SDE-2', hint: 'Around 2–5 years of experience' },
];

const LANGUAGES: Array<{ value: CodeLanguage; label: string }> = [
  { value: 'PYTHON', label: 'Python 3' },
  { value: 'JAVA', label: 'Java' },
  { value: 'CPP', label: 'C++' },
  { value: 'JAVASCRIPT', label: 'JavaScript' },
];

interface ProfileFormProps {
  initial: ProfileResponse | null | undefined;
  submitLabel: string;
  onSaved: (profile: ProfileResponse) => void;
}

export function ProfileForm({ initial, submitLabel, onSaved }: ProfileFormProps) {
  const companies = useQuery({ queryKey: ['companies'], queryFn: catalogueApi.companies, staleTime: 60 * 60 * 1000 });
  const [targetRole, setTargetRole] = useState<TargetRole>(initial?.targetRole ?? 'SDE_1');
  const [experience, setExperience] = useState<string>(initial ? String(initial.experienceYears) : '0');
  const [language, setLanguage] = useState<CodeLanguage>(initial?.preferredLanguage ?? 'PYTHON');
  const [selected, setSelected] = useState<string[]>(initial?.targetCompanies.map((c) => c.id) ?? []);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function toggleCompany(id: string) {
    setSelected((current) => (current.includes(id) ? current.filter((c) => c !== id) : [...current, id]));
  }

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    const years = Number(experience);
    const next: Record<string, string> = {};
    if (experience.trim() === '' || Number.isNaN(years) || years < 0 || years > 30) {
      next.experienceYears = 'Enter a number between 0 and 30.';
    } else if (!Number.isInteger(years * 2)) {
      next.experienceYears = 'Use steps of 0.5 (for example 1.5).';
    }
    setErrors(next);
    setFormError(null);
    if (Object.keys(next).length > 0) return;

    setSubmitting(true);
    try {
      const profile = await meApi.updateProfile({
        targetRole,
        experienceYears: years,
        preferredLanguage: language,
        targetCompanyIds: selected,
      });
      onSaved(profile);
    } catch (error) {
      if (error instanceof ApiError && error.code === 'VALIDATION_ERROR') {
        setErrors(error.fieldErrors());
      }
      setFormError(describeError(error));
    } finally {
      setSubmitting(false);
    }
  }

  const limitReached = selected.length >= MAX_COMPANIES;

  return (
    <form noValidate onSubmit={onSubmit} className="space-y-6">
      {formError && <Alert>{formError}</Alert>}

      <fieldset>
        <legend className="nb-label">Target role</legend>
        <div className="mt-2 grid gap-3 sm:grid-cols-2">
          {ROLES.map((role) => (
            <label
              key={role.value}
              className="nb-card-sm grid cursor-pointer grid-cols-[auto_1fr] gap-x-3 p-3 has-[:checked]:bg-nb-yellow has-[:checked]:text-[#0a0a0a]"
            >
              <input
                type="radio"
                name="targetRole"
                value={role.value}
                checked={targetRole === role.value}
                onChange={() => setTargetRole(role.value)}
                className="nb-check row-span-2 mt-1"
              />
              <span className="font-bold">{role.label}</span>
              <span className="col-start-2 text-sm font-medium">{role.hint}</span>
            </label>
          ))}
        </div>
      </fieldset>

      <div className="grid gap-4 sm:grid-cols-2">
        <div>
          <label htmlFor="experienceYears" className="nb-label">
            Years of experience
          </label>
          <input
            id="experienceYears"
            type="number"
            inputMode="decimal"
            min={0}
            max={30}
            step={0.5}
            value={experience}
            onChange={(e) => setExperience(e.target.value)}
            aria-invalid={errors.experienceYears ? true : undefined}
            aria-describedby={errors.experienceYears ? 'experienceYears-error' : undefined}
            className="nb-input"
          />
          {errors.experienceYears && (
            <p id="experienceYears-error" className="mt-1.5 inline-block bg-nb-red px-1.5 text-sm font-bold text-[#0a0a0a]">
              {errors.experienceYears}
            </p>
          )}
        </div>
        <div>
          <label htmlFor="preferredLanguage" className="nb-label">
            Preferred coding language
          </label>
          <select
            id="preferredLanguage"
            value={language}
            onChange={(e) => setLanguage(e.target.value as CodeLanguage)}
            className="nb-input"
          >
            {LANGUAGES.map((l) => (
              <option key={l.value} value={l.value}>
                {l.label}
              </option>
            ))}
          </select>
        </div>
      </div>

      <fieldset aria-describedby="companies-count">
        <legend className="nb-label">Target companies (optional)</legend>
        <p id="companies-count" aria-live="polite" className="mt-1 font-mono text-xs font-bold">
          {selected.length} of {MAX_COMPANIES} selected
        </p>
        {companies.isPending && <p className="mt-2 text-sm font-medium text-muted">Loading companies…</p>}
        {companies.isError && <Alert>Couldn't load the company list. You can still save your profile.</Alert>}
        {errors.targetCompanyIds && (
          <p className="mt-1.5 inline-block bg-nb-red px-1.5 text-sm font-bold text-[#0a0a0a]">{errors.targetCompanyIds}</p>
        )}
        <div className="mt-2 grid grid-cols-2 gap-2 sm:grid-cols-3">
          {companies.data?.map((company) => {
            const checked = selected.includes(company.id);
            return (
              <label key={company.id} className="flex items-center gap-2 border-2 border-transparent px-1 text-sm font-medium has-[:checked]:border-ink has-[:checked]:bg-nb-yellow has-[:checked]:text-[#0a0a0a]">
                <input
                  type="checkbox"
                  checked={checked}
                  disabled={!checked && limitReached}
                  onChange={() => toggleCompany(company.id)}
                  className="nb-check"
                />
                {company.name}
              </label>
            );
          })}
        </div>
      </fieldset>

      <button type="submit" disabled={submitting} className={buttonClass.primary}>
        {submitting ? 'Saving…' : submitLabel}
      </button>
    </form>
  );
}

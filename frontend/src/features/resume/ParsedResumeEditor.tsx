import { useState, type FormEvent, type ReactNode } from 'react';
import { ApiError } from '../../api/http';
import type { ParsedResume, SkillCategory } from '../../api/models';
import { Alert } from '../../components/ui/form';
import { buttonClass } from '../../components/ui/styles';
import { describeError } from '../auth/errorMessages';

const CATEGORIES: SkillCategory[] = ['LANGUAGE', 'FRAMEWORK', 'DATABASE', 'CLOUD', 'TOOL', 'CONCEPT'];

const lines = (text: string) => text.split('\n').map((l) => l.trim()).filter(Boolean);
const csv = (text: string) => text.split(',').map((l) => l.trim()).filter(Boolean);

interface Draft {
  summary: string;
  skills: Array<{ name: string; category: string }>;
  projects: Array<{ name: string; summary: string; techStack: string; highlights: string }>;
  experience: Array<{ company: string; title: string; start: string; end: string; highlights: string }>;
}

function toDraft(p: ParsedResume): Draft {
  return {
    summary: p.summary ?? '',
    skills: p.skills.map((s) => ({ ...s })),
    projects: p.projects.map((x) => ({ name: x.name, summary: x.summary, techStack: x.techStack.join(', '), highlights: x.highlights.join('\n') })),
    experience: p.experience.map((x) => ({ company: x.company, title: x.title, start: x.start ?? '', end: x.end ?? '', highlights: x.highlights.join('\n') })),
  };
}

function fromDraft(d: Draft, original: ParsedResume): ParsedResume {
  return {
    summary: d.summary.trim() || undefined,
    skills: d.skills.filter((s) => s.name.trim()).map((s) => ({ name: s.name.trim(), category: s.category })),
    projects: d.projects
      .filter((p) => p.name.trim())
      .map((p) => ({ name: p.name.trim(), summary: p.summary.trim(), techStack: csv(p.techStack), highlights: lines(p.highlights) })),
    experience: d.experience
      .filter((e) => e.company.trim())
      .map((e) => ({
        company: e.company.trim(),
        title: e.title.trim(),
        ...(e.start.trim() ? { start: e.start.trim() } : {}),
        ...(e.end.trim() ? { end: e.end.trim() } : {}),
        highlights: lines(e.highlights),
      })),
    integrityFlags: original.integrityFlags ?? [],
  };
}

function Field({ id, label, children }: { id: string; label: string; children: ReactNode }) {
  return (
    <div>
      <label htmlFor={id} className="nb-label">
        {label}
      </label>
      {children}
    </div>
  );
}

/** Lets the candidate correct what the parser extracted (PATCH /resumes/{id}/parsed). */
export function ParsedResumeEditor({ parsed, onSave, onCancel }: { parsed: ParsedResume; onSave: (p: ParsedResume) => Promise<unknown>; onCancel: () => void }) {
  const [draft, setDraft] = useState<Draft>(() => toDraft(parsed));
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      await onSave(fromDraft(draft, parsed));
    } catch (err) {
      setError(err);
    } finally {
      setSaving(false);
    }
  }

  const fieldErrors = error instanceof ApiError ? Object.entries(error.fieldErrors()) : [];

  return (
    <form onSubmit={submit} className="space-y-6">
      <Field id="r-summary" label="Summary">
        <textarea id="r-summary" className="nb-input min-h-24" maxLength={600} value={draft.summary} onChange={(e) => setDraft({ ...draft, summary: e.target.value })} />
      </Field>

      <fieldset className="space-y-2">
        <legend className="nb-heading text-lg">Skills</legend>
        {draft.skills.map((s, i) => (
          <div key={i} className="flex flex-wrap items-end gap-2">
            <div className="flex-1">
              <Field id={`skill-${i}`} label={`Skill ${i + 1}`}>
                <input
                  id={`skill-${i}`}
                  className="nb-input"
                  maxLength={40}
                  value={s.name}
                  onChange={(e) => setDraft({ ...draft, skills: draft.skills.map((x, j) => (j === i ? { ...x, name: e.target.value } : x)) })}
                />
              </Field>
            </div>
            <Field id={`skill-cat-${i}`} label="Category">
              <select
                id={`skill-cat-${i}`}
                className="nb-input"
                value={s.category}
                onChange={(e) => setDraft({ ...draft, skills: draft.skills.map((x, j) => (j === i ? { ...x, category: e.target.value } : x)) })}
              >
                {CATEGORIES.map((c) => (
                  <option key={c} value={c}>
                    {c.toLowerCase()}
                  </option>
                ))}
              </select>
            </Field>
            <button
              type="button"
              className={`${buttonClass.secondary} nb-btn-sm`}
              onClick={() => setDraft({ ...draft, skills: draft.skills.filter((_, j) => j !== i) })}
              aria-label={`Remove skill ${s.name || i + 1}`}
            >
              Remove
            </button>
          </div>
        ))}
        <button
          type="button"
          className={`${buttonClass.secondary} nb-btn-sm`}
          disabled={draft.skills.length >= 60}
          onClick={() => setDraft({ ...draft, skills: [...draft.skills, { name: '', category: 'LANGUAGE' }] })}
        >
          + Add skill
        </button>
      </fieldset>

      <fieldset className="space-y-4">
        <legend className="nb-heading text-lg">Projects</legend>
        {draft.projects.map((p, i) => {
          const set = (patch: Partial<Draft['projects'][number]>) =>
            setDraft({ ...draft, projects: draft.projects.map((x, j) => (j === i ? { ...x, ...patch } : x)) });
          return (
            <div key={i} className="grid gap-3 border-2 border-ink p-3 sm:grid-cols-2">
              <Field id={`p-name-${i}`} label="Project name">
                <input id={`p-name-${i}`} className="nb-input" maxLength={80} value={p.name} onChange={(e) => set({ name: e.target.value })} />
              </Field>
              <Field id={`p-tech-${i}`} label="Tech stack (comma-separated)">
                <input id={`p-tech-${i}`} className="nb-input" value={p.techStack} onChange={(e) => set({ techStack: e.target.value })} />
              </Field>
              <div className="sm:col-span-2">
                <Field id={`p-summary-${i}`} label="Summary">
                  <textarea id={`p-summary-${i}`} className="nb-input min-h-20" maxLength={500} value={p.summary} onChange={(e) => set({ summary: e.target.value })} />
                </Field>
              </div>
              <div className="sm:col-span-2">
                <Field id={`p-high-${i}`} label="Highlights (one per line)">
                  <textarea id={`p-high-${i}`} className="nb-input min-h-20" value={p.highlights} onChange={(e) => set({ highlights: e.target.value })} />
                </Field>
              </div>
              <button
                type="button"
                className={`${buttonClass.secondary} nb-btn-sm w-fit`}
                onClick={() => setDraft({ ...draft, projects: draft.projects.filter((_, j) => j !== i) })}
              >
                Remove project<span className="sr-only"> {p.name}</span>
              </button>
            </div>
          );
        })}
        <button
          type="button"
          className={`${buttonClass.secondary} nb-btn-sm`}
          disabled={draft.projects.length >= 10}
          onClick={() => setDraft({ ...draft, projects: [...draft.projects, { name: '', summary: '', techStack: '', highlights: '' }] })}
        >
          + Add project
        </button>
      </fieldset>

      <fieldset className="space-y-4">
        <legend className="nb-heading text-lg">Experience</legend>
        {draft.experience.map((x, i) => {
          const set = (patch: Partial<Draft['experience'][number]>) =>
            setDraft({ ...draft, experience: draft.experience.map((e, j) => (j === i ? { ...e, ...patch } : e)) });
          return (
            <div key={i} className="grid gap-3 border-2 border-ink p-3 sm:grid-cols-2">
              <Field id={`x-company-${i}`} label="Company">
                <input id={`x-company-${i}`} className="nb-input" maxLength={80} value={x.company} onChange={(e) => set({ company: e.target.value })} />
              </Field>
              <Field id={`x-title-${i}`} label="Title">
                <input id={`x-title-${i}`} className="nb-input" maxLength={80} value={x.title} onChange={(e) => set({ title: e.target.value })} />
              </Field>
              <Field id={`x-start-${i}`} label="Start (YYYY-MM)">
                <input id={`x-start-${i}`} className="nb-input" maxLength={10} pattern="\d{4}-\d{2}" value={x.start} onChange={(e) => set({ start: e.target.value })} />
              </Field>
              <Field id={`x-end-${i}`} label="End (YYYY-MM or present)">
                <input id={`x-end-${i}`} className="nb-input" maxLength={10} pattern="\d{4}-\d{2}|present" value={x.end} onChange={(e) => set({ end: e.target.value })} />
              </Field>
              <div className="sm:col-span-2">
                <Field id={`x-high-${i}`} label="Highlights (one per line)">
                  <textarea id={`x-high-${i}`} className="nb-input min-h-20" value={x.highlights} onChange={(e) => set({ highlights: e.target.value })} />
                </Field>
              </div>
              <button
                type="button"
                className={`${buttonClass.secondary} nb-btn-sm w-fit`}
                onClick={() => setDraft({ ...draft, experience: draft.experience.filter((_, j) => j !== i) })}
              >
                Remove role<span className="sr-only"> at {x.company}</span>
              </button>
            </div>
          );
        })}
        <button
          type="button"
          className={`${buttonClass.secondary} nb-btn-sm`}
          disabled={draft.experience.length >= 10}
          onClick={() => setDraft({ ...draft, experience: [...draft.experience, { company: '', title: '', start: '', end: '', highlights: '' }] })}
        >
          + Add role
        </button>
      </fieldset>

      {error !== null && (
        <Alert>
          {describeError(error)}
          {fieldErrors.length > 0 && (
            <ul className="mt-1 list-disc pl-5">
              {fieldErrors.map(([field, message]) => (
                <li key={field}>
                  <span className="font-mono">{field}</span>: {message}
                </li>
              ))}
            </ul>
          )}
        </Alert>
      )}
      <div className="flex gap-3">
        <button type="submit" className={buttonClass.primary} disabled={saving}>
          {saving ? 'Saving…' : 'Save corrections'}
        </button>
        <button type="button" className={buttonClass.secondary} onClick={onCancel} disabled={saving}>
          Cancel
        </button>
      </div>
    </form>
  );
}

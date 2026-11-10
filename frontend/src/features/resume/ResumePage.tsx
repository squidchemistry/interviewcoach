import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type DragEvent } from 'react';
import { Link } from 'react-router-dom';
import { resumesApi } from '../../api/endpoints';
import type { ResumeResponse } from '../../api/models';
import { ConfirmDialog } from '../../components/ui/ConfirmDialog';
import { EmptyState } from '../../components/ui/EmptyState';
import { Alert } from '../../components/ui/form';
import { PageHeader } from '../../components/ui/PageHeader';
import { accents, buttonClass } from '../../components/ui/styles';
import { formatDateTime } from '../../lib/labels';
import { describeError } from '../auth/errorMessages';
import { ParsedResumeEditor } from './ParsedResumeEditor';
import { integrityNotes, validateResumeFile } from './validation';

const POLL_MS = 2500;
const STATUS_COLOR: Record<string, string> = { PARSING: 'bg-nb-yellow', PARSED: 'bg-nb-green', FAILED: 'bg-nb-red' };

function UploadZone({ onUploaded }: { onUploaded: (r: ResumeResponse) => void }) {
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);
  const [clientError, setClientError] = useState<string | null>(null);
  const upload = useMutation({ mutationFn: resumesApi.upload, onSuccess: onUploaded });

  function accept(file: File | undefined) {
    if (!file) return;
    const problem = validateResumeFile(file);
    setClientError(problem);
    if (!problem) upload.mutate(file);
  }

  function onDrop(event: DragEvent<HTMLDivElement>) {
    event.preventDefault();
    setDragging(false);
    accept(event.dataTransfer.files[0]);
  }

  return (
    <section aria-labelledby="upload-heading" className="space-y-3">
      <h2 id="upload-heading" className="nb-heading text-2xl">
        Upload a resume
      </h2>
      <div
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        className={`nb-card flex flex-col items-center gap-3 border-dashed p-8 text-center ${dragging ? 'bg-nb-yellow text-[#0a0a0a]' : ''}`}
      >
        <p className="text-lg font-bold">Drag & drop your PDF here</p>
        <p className="text-sm font-medium">PDF only · up to 5 MB · up to 10 pages</p>
        <label htmlFor="resume-file" className={`${buttonClass.primary} cursor-pointer`}>
          {upload.isPending ? 'Uploading…' : 'Choose a PDF'}
        </label>
        <input
          ref={input}
          id="resume-file"
          type="file"
          accept="application/pdf,.pdf"
          className="sr-only"
          disabled={upload.isPending}
          onChange={(e) => {
            accept(e.target.files?.[0]);
            e.target.value = '';
          }}
        />
      </div>
      {clientError && <Alert>{clientError}</Alert>}
      {upload.isError && <Alert>{describeError(upload.error)}</Alert>}
      <p className="text-xs font-medium text-muted">
        We extract skills, projects and experience to tailor resume deep-dive interviews. The PDF itself is never shown to the
        interviewer, and deleting a resume removes the file and its text.
      </p>
    </section>
  );
}

function ParsedView({ resume }: { resume: ResumeResponse }) {
  const parsed = resume.parsed;
  if (!parsed) return null;
  return (
    <div className="space-y-5">
      {parsed.summary && <p className="text-lg font-medium">{parsed.summary}</p>}
      {parsed.skills.length > 0 && (
        <div>
          <h3 className="font-bold">Skills</h3>
          <ul className="mt-2 flex flex-wrap gap-2" aria-label="Skills">
            {parsed.skills.map((s, i) => (
              <li key={`${s.name}-${i}`} className={`nb-badge ${accents[['LANGUAGE', 'FRAMEWORK', 'DATABASE', 'CLOUD', 'TOOL', 'CONCEPT'].indexOf(s.category) % accents.length] ?? 'bg-nb-blue'}`}>
                {s.name}
              </li>
            ))}
          </ul>
        </div>
      )}
      {parsed.projects.length > 0 && (
        <div>
          <h3 className="font-bold">Projects</h3>
          <ul className="mt-2 grid gap-3 md:grid-cols-2">
            {parsed.projects.map((p, i) => (
              <li key={`${p.name}-${i}`} className="border-2 border-ink p-3">
                <p className="font-bold">{p.name}</p>
                <p className="mt-1 text-sm font-medium">{p.summary}</p>
                {p.techStack.length > 0 && <p className="mt-1 font-mono text-xs">{p.techStack.join(' · ')}</p>}
                {p.highlights.length > 0 && (
                  <ul className="mt-1 list-disc pl-5 text-sm">
                    {p.highlights.map((h) => (
                      <li key={h}>{h}</li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
      {parsed.experience.length > 0 && (
        <div>
          <h3 className="font-bold">Experience</h3>
          <ul className="mt-2 space-y-3">
            {parsed.experience.map((x, i) => (
              <li key={`${x.company}-${i}`} className="border-2 border-ink p-3">
                <p className="font-bold">
                  {x.title} · {x.company}
                </p>
                <p className="font-mono text-xs">
                  {x.start ?? '?'} – {x.end ?? '?'}
                </p>
                {x.highlights.length > 0 && (
                  <ul className="mt-1 list-disc pl-5 text-sm">
                    {x.highlights.map((h) => (
                      <li key={h}>{h}</li>
                    ))}
                  </ul>
                )}
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}

export function ResumePage() {
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [editing, setEditing] = useState(false);
  const [deleting, setDeleting] = useState<ResumeResponse | null>(null);

  const resumes = useQuery({
    queryKey: ['resumes'],
    queryFn: resumesApi.list,
    refetchInterval: (query) => (query.state.data?.some((r) => r.status === 'PARSING') ? POLL_MS : false),
  });
  const invalidate = () => queryClient.invalidateQueries({ queryKey: ['resumes'] });
  const activate = useMutation({ mutationFn: resumesApi.activate, onSuccess: invalidate });
  const remove = useMutation({
    mutationFn: resumesApi.remove,
    onSuccess: () => {
      setDeleting(null);
      setSelectedId(null);
      void invalidate();
    },
  });
  const save = useMutation({
    mutationFn: ({ id, parsed }: { id: string; parsed: NonNullable<ResumeResponse['parsed']> }) => resumesApi.updateParsed(id, parsed),
    onSuccess: () => {
      setEditing(false);
      void invalidate();
    },
  });

  const list = resumes.data ?? [];
  const selected = list.find((r) => r.id === selectedId) ?? list.find((r) => r.active) ?? list[0];
  const notes = selected ? integrityNotes(selected.integrity).concat(selected.parsed?.integrityFlags ?? []) : [];

  return (
    <div className="mx-auto max-w-6xl space-y-8 px-4 py-10 sm:px-6">
      <PageHeader
        title="Your resume"
        kicker="Resume"
        kickerColor="bg-nb-green"
        description="Your active resume powers the resume deep-dive round."
        actions={
          <Link to="/interviews/new?mode=RESUME_DEEP_DIVE" className={buttonClass.primary}>
            Start a resume deep-dive
          </Link>
        }
      />
      <UploadZone
        onUploaded={(r) => {
          setSelectedId(r.id);
          void invalidate();
        }}
      />

      {resumes.isError && <Alert>{describeError(resumes.error)}</Alert>}
      {activate.isError && <Alert>{describeError(activate.error)}</Alert>}
      {remove.isError && <Alert>{describeError(remove.error)}</Alert>}

      {resumes.data && list.length === 0 && (
        <EmptyState title="No resumes yet">Upload a PDF above to get personalised resume deep-dive interviews.</EmptyState>
      )}

      {list.length > 0 && (
        <section aria-labelledby="resumes-heading">
          <h2 id="resumes-heading" className="nb-heading text-2xl">
            Uploaded resumes
          </h2>
          <div className="nb-card mt-4 overflow-x-auto">
            <table className="nb-table min-w-full text-sm">
              <thead>
                <tr>
                  <th scope="col">File</th>
                  <th scope="col">Status</th>
                  <th scope="col">Uploaded</th>
                  <th scope="col">
                    <span className="sr-only">Actions</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {list.map((r) => (
                  <tr key={r.id} className={selected?.id === r.id ? 'bg-nb-yellow/30' : ''}>
                    <td className="font-bold">
                      <button type="button" className="text-left underline" onClick={() => { setSelectedId(r.id); setEditing(false); }}>
                        {r.filename}
                      </button>
                      {r.active && <span className="nb-badge ml-2 bg-nb-violet">Active</span>}
                      <span className="block font-mono text-xs font-normal">
                        {(r.sizeBytes / 1024).toFixed(0)} KB{r.pageCount ? ` · ${r.pageCount} pages` : ''}
                      </span>
                    </td>
                    <td>
                      <span className={`nb-badge ${STATUS_COLOR[r.status] ?? 'bg-nb-blue'}`}>{r.status === 'PARSING' ? 'Parsing…' : r.status.toLowerCase()}</span>
                      {r.status === 'FAILED' && r.error && <span className="mt-1 block text-xs">{r.error}</span>}
                    </td>
                    <td className="whitespace-nowrap font-mono text-xs">{formatDateTime(r.createdAt)}</td>
                    <td className="space-x-2 whitespace-nowrap text-right">
                      {!r.active && r.status === 'PARSED' && (
                        <button type="button" className={`${buttonClass.secondary} nb-btn-sm`} disabled={activate.isPending} onClick={() => activate.mutate(r.id)}>
                          Make active<span className="sr-only">: {r.filename}</span>
                        </button>
                      )}
                      <button type="button" className={`${buttonClass.danger} nb-btn-sm`} onClick={() => setDeleting(r)}>
                        Delete<span className="sr-only">: {r.filename}</span>
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </section>
      )}

      {selected && (
        <section aria-labelledby="parsed-heading" className="nb-card p-6">
          <div className="flex flex-wrap items-center justify-between gap-3">
            <h2 id="parsed-heading" className="nb-heading text-2xl">
              {selected.filename}
            </h2>
            {selected.status === 'PARSED' && selected.parsed && !editing && (
              <button type="button" className={`${buttonClass.secondary} nb-btn-sm`} onClick={() => setEditing(true)}>
                Correct parsed data
              </button>
            )}
          </div>
          <div className="mt-4">
            {selected.status === 'PARSING' && (
              <p role="status" className="font-bold">
                Reading your resume… this usually takes a few seconds.
              </p>
            )}
            {selected.status === 'FAILED' && <Alert>{selected.error ?? 'We could not read this PDF. Try exporting it again as a text-based PDF.'}</Alert>}
            {selected.status === 'PARSED' &&
              selected.parsed &&
              (editing ? (
                <ParsedResumeEditor
                  parsed={selected.parsed}
                  onSave={(parsed) => save.mutateAsync({ id: selected.id, parsed })}
                  onCancel={() => setEditing(false)}
                />
              ) : (
                <ParsedView resume={selected} />
              ))}
          </div>
          {notes.length > 0 && (
            <div className="mt-6 border-2 border-dashed border-ink p-4">
              <h3 className="font-bold">Notes from parsing</h3>
              <p className="text-xs font-medium text-muted">Context only — things the parser couldn't verify or found unusual.</p>
              <ul className="mt-2 list-disc pl-5 text-sm">
                {notes.map((n) => (
                  <li key={n}>{n}</li>
                ))}
              </ul>
            </div>
          )}
        </section>
      )}

      <ConfirmDialog
        open={deleting !== null}
        title="Delete this resume?"
        confirmLabel="Delete"
        busy={remove.isPending}
        onConfirm={() => deleting && remove.mutate(deleting.id)}
        onCancel={() => setDeleting(null)}
      >
        {deleting?.filename} and its extracted text will be permanently deleted.
      </ConfirmDialog>
    </div>
  );
}

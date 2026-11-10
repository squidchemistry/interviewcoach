import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { http, HttpResponse } from 'msw';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { ResumeResponse } from '../../api/models';
import { setSession } from '../../api/session';
import { makeAuth } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { server } from '../../test/server';
import { ResumePage } from './ResumePage';
import { MAX_RESUME_BYTES, validateResumeFile } from './validation';

function pdf(size: number, name = 'cv.pdf', type = 'application/pdf') {
  const file = new File(['%PDF'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}

describe('validateResumeFile', () => {
  it('accepts PDFs up to 5 MB and rejects other types, empty and oversized files', () => {
    expect(validateResumeFile(pdf(1000))).toBeNull();
    expect(validateResumeFile(pdf(MAX_RESUME_BYTES))).toBeNull();
    expect(validateResumeFile(pdf(MAX_RESUME_BYTES + 1))).toBe('The PDF must be 5 MB or smaller.');
    expect(validateResumeFile(pdf(1000, 'cv.docx', 'application/vnd.openxmlformats-officedocument.wordprocessingml.document'))).toBe(
      'Only PDF files are supported.',
    );
    expect(validateResumeFile(pdf(0))).toBe('That file is empty.');
  });
});

describe('ResumePage', () => {
  const parsing: ResumeResponse = { id: 'r-1', filename: 'cv.pdf', sizeBytes: 2048, pageCount: 1, status: 'PARSING', active: false, createdAt: '2026-11-06T10:00:00Z' };

  beforeEach(() => {
    setSession(makeAuth());
    server.use(http.post('*/api/v1/auth/refresh', () => HttpResponse.json(makeAuth())));
  });

  it('rejects an oversized file on the client without uploading it', async () => {
    let uploads = 0;
    server.use(
      http.get('*/api/v1/resumes', () => HttpResponse.json([])),
      http.post('*/api/v1/resumes', () => {
        uploads += 1;
        return HttpResponse.json(parsing, { status: 202 });
      }),
    );
    renderWithProviders(<ResumePage />);
    expect(await screen.findByRole('heading', { name: 'No resumes yet' })).toBeInTheDocument();

    await userEvent.upload(screen.getByLabelText('Choose a PDF'), pdf(MAX_RESUME_BYTES + 10));
    expect(await screen.findByRole('alert')).toHaveTextContent('The PDF must be 5 MB or smaller.');
    expect(uploads).toBe(0);
  });

  it('uploads a valid PDF as multipart and shows it parsing', async () => {
    let list: ResumeResponse[] = [];
    server.use(
      http.get('*/api/v1/resumes', () => HttpResponse.json(list)),
      http.post('*/api/v1/resumes', () => {
        list = [parsing];
        return HttpResponse.json(parsing, { status: 202 });
      }),
    );
    // Under jsdom, FormData is jsdom's and fetch is Node's, so inspect what the client hands to fetch instead of
    // what reaches the mock server: a FormData body and NO explicit Content-Type (the browser adds the boundary).
    const fetchSpy = vi.spyOn(globalThis, 'fetch');
    renderWithProviders(<ResumePage />);
    await screen.findByRole('heading', { name: 'No resumes yet' });
    const file = pdf(4096);
    await userEvent.upload(screen.getByLabelText('Choose a PDF'), file);

    expect(await screen.findByText('Parsing…')).toBeInTheDocument();
    const upload = fetchSpy.mock.calls.find(([, init]) => init?.method === 'POST' && init.body instanceof FormData);
    expect(upload).toBeDefined();
    const init = upload![1]!;
    expect((init.body as FormData).get('file')).toBe(file);
    expect(Object.keys(init.headers as Record<string, string>).map((h) => h.toLowerCase())).not.toContain('content-type');
    expect(screen.getByText(/Reading your resume/)).toBeInTheDocument();
  });
});

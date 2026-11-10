/**
 * Monaco, bundled locally (no CDN): @monaco-editor/react is pointed at the npm package and the workers are built by
 * Vite. Loaded lazily, so only DSA rooms pay for it.
 */
import Editor, { loader, type OnMount } from '@monaco-editor/react';
// Core editor + only the four interview languages (the full package bundles ~80 languages and LSP clients).
import * as monaco from 'monaco-editor/editor/editor.api';
import type * as MonacoApi from 'monaco-editor';
import 'monaco-editor/languages/definitions/python/register';
import 'monaco-editor/languages/definitions/java/register';
import 'monaco-editor/languages/definitions/cpp/register';
import 'monaco-editor/languages/definitions/javascript/register';
import EditorWorker from 'monaco-editor/editor/editor.worker?worker';
import { useEffect, useState } from 'react';
import type { CodeLanguage } from '../../../api/types';

(self as unknown as { MonacoEnvironment: { getWorker: (id: string, label: string) => Worker } }).MonacoEnvironment = {
  getWorker() {
    return new EditorWorker();
  },
};
loader.config({ monaco: monaco as unknown as typeof MonacoApi });

const MONACO_LANGUAGE: Record<CodeLanguage, string> = {
  PYTHON: 'python',
  JAVA: 'java',
  CPP: 'cpp',
  JAVASCRIPT: 'javascript',
};

function usePrefersDark() {
  const query = typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null;
  const [dark, setDark] = useState(query?.matches ?? false);
  useEffect(() => {
    if (!query) return;
    const onChange = (e: MediaQueryListEvent) => setDark(e.matches);
    query.addEventListener('change', onChange);
    return () => query.removeEventListener('change', onChange);
  }, [query]);
  return dark;
}

export default function CodeEditor({
  language,
  value,
  onChange,
  onPaste,
}: {
  language: CodeLanguage;
  value: string;
  onChange: (value: string) => void;
  onPaste: (size: number) => void;
}) {
  const dark = usePrefersDark();
  const onMount: OnMount = (editor) => {
    editor.onDidPaste((event) => {
      const model = editor.getModel();
      if (model) onPaste(model.getValueInRange(event.range).length);
    });
  };
  return (
    <Editor
      height="420px"
      language={MONACO_LANGUAGE[language]}
      value={value}
      theme={dark ? 'vs-dark' : 'vs'}
      onChange={(v) => onChange(v ?? '')}
      onMount={onMount}
      loading={<p className="p-4 font-mono text-sm font-bold">Loading editor…</p>}
      options={{
        ariaLabel: 'Code editor',
        minimap: { enabled: false },
        fontSize: 14,
        fontFamily: "'Space Mono', ui-monospace, monospace",
        scrollBeyondLastLine: false,
        automaticLayout: true,
        tabSize: language === 'PYTHON' ? 4 : 2,
        accessibilitySupport: 'auto',
      }}
    />
  );
}

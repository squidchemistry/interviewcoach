import ReactMarkdown, { type Components } from 'react-markdown';
import remarkGfm from 'remark-gfm';

/**
 * Markdown renderer for questions, interviewer turns and model answers. Raw HTML is NOT enabled (react-markdown
 * escapes it by default), so AI or user content cannot inject markup.
 */
const components: Components = {
  h1: ({ children }) => <h3 className="nb-heading mt-4 mb-2 text-xl">{children}</h3>,
  h2: ({ children }) => <h3 className="nb-heading mt-4 mb-2 text-lg">{children}</h3>,
  h3: ({ children }) => <h4 className="mt-3 mb-1.5 font-bold">{children}</h4>,
  h4: ({ children }) => <h5 className="mt-3 mb-1 font-bold">{children}</h5>,
  p: ({ children }) => <p className="my-2 leading-relaxed">{children}</p>,
  ul: ({ children }) => <ul className="my-2 list-disc space-y-1 pl-6">{children}</ul>,
  ol: ({ children }) => <ol className="my-2 list-decimal space-y-1 pl-6">{children}</ol>,
  a: ({ children, href }) => (
    <a href={href} className="nb-link" target="_blank" rel="noopener noreferrer">
      {children}
    </a>
  ),
  blockquote: ({ children }) => <blockquote className="my-2 border-l-4 border-ink pl-3 italic">{children}</blockquote>,
  pre: ({ children }) => (
    <pre className="my-2 overflow-x-auto border-2 border-ink bg-paper p-3 font-mono text-xs leading-relaxed">{children}</pre>
  ),
  code: ({ children, className }) =>
    className ? (
      <code className={className}>{children}</code>
    ) : (
      <code className="border border-ink/40 bg-paper px-1 font-mono text-[0.85em]">{children}</code>
    ),
  table: ({ children }) => (
    <div className="my-2 overflow-x-auto">
      <table className="nb-table text-sm">{children}</table>
    </div>
  ),
  hr: () => <hr className="my-4 border-t-2 border-ink" />,
};

export function Markdown({ children, className = '' }: { children: string; className?: string }) {
  return (
    <div className={`break-words text-[0.95rem] ${className}`}>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={components}>
        {children}
      </ReactMarkdown>
    </div>
  );
}

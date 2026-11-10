/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** Absolute API origin for cross-origin deployments (e.g. Vercel web + Render API). Empty = same origin. */
  readonly VITE_API_BASE_URL?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}

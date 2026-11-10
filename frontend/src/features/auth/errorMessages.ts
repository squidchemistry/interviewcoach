import { ApiError } from '../../api/http';

/** Human-friendly message for an API failure; falls back to the server's safe `detail`. */
export function describeError(error: unknown): string {
  if (!(error instanceof ApiError)) {
    return 'Something went wrong. Please try again.';
  }
  switch (error.code) {
    case 'NETWORK_ERROR':
      return "Can't reach the server. Check your connection and try again.";
    case 'INVALID_CREDENTIALS':
      return 'Email or password is incorrect.';
    case 'RATE_LIMITED':
      return error.message || 'Too many attempts. Please wait a moment and try again.';
    case 'VALIDATION_ERROR':
      return 'Please fix the highlighted fields.';
    case 'INTERNAL':
      return `Something went wrong on our side${error.requestId ? ` (reference ${error.requestId})` : ''}.`;
    default:
      return error.message;
  }
}

export const GOOGLE_ERRORS: Record<string, string> = {
  google_failed: 'Google sign-in failed. Please try again or use your email and password.',
  google_cancelled: 'Google sign-in was cancelled.',
};

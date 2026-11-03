// API base URL comes from REACT_APP_API_URL at build time (set it in Vercel).
// Fallbacks: same origin in production builds, the local API in development.
export const API_BASE_URL = (
  process.env.REACT_APP_API_URL ||
  (process.env.NODE_ENV === 'production' ? window.location.origin : 'http://localhost:8000')
).replace(/\/+$/, '');

export class ApiError extends Error {
  constructor(message, status, code) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

// The backend returns errors as {"error": {"code", "message", "details"}}.
async function request(path, { method = 'GET', body } = {}) {
  let response;
  try {
    response = await fetch(`${API_BASE_URL}${path}`, {
      method,
      headers: body ? { 'Content-Type': 'application/json' } : undefined,
      body: body ? JSON.stringify(body) : undefined,
    });
  } catch (networkError) {
    throw new ApiError('Cannot reach the API. Is the backend running?', 0, 'network_error');
  }

  if (!response.ok) {
    const data = await response.json().catch(() => ({}));
    const error = data.error || {};
    throw new ApiError(
      error.message || `Request failed (${response.status})`,
      response.status,
      error.code || 'http_error'
    );
  }
  return response.json();
}

export const sessionsAPI = {
  create(role, experience, numQuestions = 5) {
    return request('/v1/sessions', {
      method: 'POST',
      body: { role, experience, num_questions: numQuestions },
    });
  },

  list({ limit = 10, offset = 0 } = {}) {
    return request(`/v1/sessions?limit=${limit}&offset=${offset}`);
  },

  get(sessionId) {
    return request(`/v1/sessions/${encodeURIComponent(sessionId)}`);
  },

  submitAnswer(sessionId, questionId, answer) {
    return request(`/v1/sessions/${encodeURIComponent(sessionId)}/answers`, {
      method: 'POST',
      body: { question_id: questionId, answer },
    });
  },

  summary(sessionId) {
    return request(`/v1/sessions/${encodeURIComponent(sessionId)}/summary`);
  },
};

export const healthAPI = {
  ready() {
    return request('/ready');
  },
};

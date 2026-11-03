import { API_BASE_URL, ApiError, sessionsAPI } from './api';

afterEach(() => {
  delete global.fetch;
});

test('list sends limit and offset', async () => {
  global.fetch = jest.fn().mockResolvedValue({ ok: true, json: async () => ({ items: [], total: 0 }) });
  await sessionsAPI.list({ limit: 10, offset: 20 });
  expect(global.fetch).toHaveBeenCalledWith(`${API_BASE_URL}/v1/sessions?limit=10&offset=20`, expect.anything());
});

test('submitAnswer posts JSON to the session', async () => {
  global.fetch = jest.fn().mockResolvedValue({ ok: true, json: async () => ({}) });
  await sessionsAPI.submitAnswer('s 1', 'q1', 'hello');
  const [url, options] = global.fetch.mock.calls[0];
  expect(url).toBe(`${API_BASE_URL}/v1/sessions/s%201/answers`);
  expect(options.method).toBe('POST');
  expect(JSON.parse(options.body)).toEqual({ question_id: 'q1', answer: 'hello' });
});

test('error responses become ApiError with the server message and code', async () => {
  global.fetch = jest.fn().mockResolvedValue({
    ok: false,
    status: 409,
    json: async () => ({ error: { code: 'conflict', message: 'Question has already been answered' } }),
  });
  await expect(sessionsAPI.get('x')).rejects.toMatchObject({
    name: 'ApiError',
    status: 409,
    code: 'conflict',
    message: 'Question has already been answered',
  });
});

test('network failures become a readable ApiError', async () => {
  global.fetch = jest.fn().mockRejectedValue(new TypeError('Failed to fetch'));
  const error = await sessionsAPI.get('x').catch((e) => e);
  expect(error).toBeInstanceOf(ApiError);
  expect(error.code).toBe('network_error');
});

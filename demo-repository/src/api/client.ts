/**
 * One API client for the whole app.
 *
 * - Same-origin requests with the RCSESSION cookie (credentials: 'same-origin').
 * - CSRF: the backend issues an XSRF-TOKEN cookie (readable by JS); every
 *   state-changing request echoes it in the X-XSRF-TOKEN header. If the cookie
 *   is missing we fetch GET /api/csrf once to obtain it.
 * - Errors: non-2xx responses become ApiError with the backend's {error, message, fields}.
 * - A 403 CSRF_INVALID (stale or missing token, e.g. after the session ended) fetches a fresh token and
 *   retries the request once. Spring Security refuses such a request before any controller runs, so the retry
 *   cannot apply a change twice. A 403 FORBIDDEN_ROLE is never retried. (AI-assisted (Claude), 2026; reviewed by the author.)
 */

export type ApiErrorBody = {
  error: string;
  message: string;
  fields?: Record<string, string>;
};

export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fields: Record<string, string>;

  constructor(status: number, body: Partial<ApiErrorBody> | undefined) {
    super(body?.message ?? `Request failed (${status})`);
    this.status = status;
    this.code = body?.error ?? (status === 401 ? 'UNAUTHENTICATED' : 'HTTP_ERROR');
    this.fields = body?.fields ?? {};
  }
}

const CSRF_COOKIE = 'XSRF-TOKEN';
const CSRF_HEADER = 'X-XSRF-TOKEN';
const MUTATING = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

const readCookie = (name: string): string | null => {
  const match = document.cookie.split('; ').find((row) => row.startsWith(`${name}=`));
  return match ? decodeURIComponent(match.slice(name.length + 1)) : null;
};

const ensureCsrfToken = async (fresh = false): Promise<string> => {
  const existing = readCookie(CSRF_COOKIE);
  if (existing && !fresh) return existing;
  await fetch('/api/csrf', { credentials: 'same-origin' });
  return readCookie(CSRF_COOKIE) ?? '';
};

const readError = async (response: Response): Promise<Partial<ApiErrorBody> | undefined> => {
  try {
    return (await response.json()) as ApiErrorBody;
  } catch {
    return undefined;
  }
};

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
  idempotencyKey?: string;
};

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.idempotencyKey) headers['Idempotency-Key'] = options.idempotencyKey;
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  const send = async (freshToken: boolean) => {
    if (MUTATING.has(method)) headers[CSRF_HEADER] = await ensureCsrfToken(freshToken);
    return fetch(path, {
      method,
      headers,
      credentials: 'same-origin',
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: options.signal,
    });
  };

  let response = await send(false);
  let body = response.ok ? undefined : await readError(response);
  if (response.status === 403 && body?.error === 'CSRF_INVALID' && MUTATING.has(method)) {
    response = await send(true);
    body = response.ok ? undefined : await readError(response);
  }
  if (!response.ok) throw new ApiError(response.status, body);

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

export async function apiMultipart<T>(path: string, file: File, fields: Record<string, string>): Promise<T> {
  const form = new FormData();
  form.append('image', file);
  Object.entries(fields).forEach(([name, value]) => form.append(name, value));
  const send = async (freshToken: boolean) => fetch(path, {
    method: 'POST', credentials: 'same-origin', body: form,
    headers: { Accept: 'application/json', [CSRF_HEADER]: await ensureCsrfToken(freshToken) },
  });
  let response = await send(false);
  let body = response.ok ? undefined : await readError(response);
  if (response.status === 403 && body?.error === 'CSRF_INVALID') {
    response = await send(true);
    body = response.ok ? undefined : await readError(response);
  }
  if (!response.ok) throw new ApiError(response.status, body);
  return (await response.json()) as T;
}

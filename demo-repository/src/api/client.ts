/**
 * One API client for the whole app.
 *
 * - Same-origin requests with the RCSESSION cookie (credentials: 'same-origin').
 * - CSRF: the backend issues an XSRF-TOKEN cookie (readable by JS); every
 *   state-changing request echoes it in the X-XSRF-TOKEN header. If the cookie
 *   is missing we fetch GET /api/csrf once to obtain it.
 * - Errors: non-2xx responses become ApiError with the backend's {error, message, fields}.
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

const ensureCsrfToken = async (): Promise<string> => {
  const existing = readCookie(CSRF_COOKIE);
  if (existing) return existing;
  await fetch('/api/csrf', { credentials: 'same-origin' });
  return readCookie(CSRF_COOKIE) ?? '';
};

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE';
  body?: unknown;
  signal?: AbortSignal;
};

export async function api<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const method = options.method ?? 'GET';
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (MUTATING.has(method)) headers[CSRF_HEADER] = await ensureCsrfToken();

  const response = await fetch(path, {
    method,
    headers,
    credentials: 'same-origin',
    body: options.body === undefined ? undefined : JSON.stringify(options.body),
    signal: options.signal,
  });

  if (!response.ok) {
    let body: Partial<ApiErrorBody> | undefined;
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      body = undefined;
    }
    throw new ApiError(response.status, body);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

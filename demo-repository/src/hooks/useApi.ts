import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '../api/client';

export type ApiState<T> =
  | { status: 'loading'; data?: undefined; error?: undefined }
  | { status: 'error'; error: ApiError | Error; data?: undefined }
  | { status: 'ready'; data: T; error?: undefined };

/** Runs a request once per dependency change and exposes loading / error / data. */
export function useApi<T>(request: () => Promise<T>, deps: unknown[]): ApiState<T> & { reload: () => void } {
  const [state, setState] = useState<ApiState<T>>({ status: 'loading' });
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setState({ status: 'loading' });
    request()
      .then((data) => !cancelled && setState({ status: 'ready', data }))
      .catch((error: unknown) =>
        !cancelled && setState({ status: 'error', error: error instanceof Error ? error : new Error(String(error)) }),
      );
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { ...state, reload };
}

export const errorMessage = (error: Error): string =>
  error instanceof ApiError && error.status === 401
    ? 'Your session has ended. Please sign in again.'
    : error.message || 'Something went wrong.';

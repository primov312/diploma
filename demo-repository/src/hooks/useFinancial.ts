import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';

export const financialChanged = () => window.dispatchEvent(new Event('financial-changed'));
/** Retains displayed data during background refresh; ignores responses from earlier filters. */
export function useFinancial<T>(request: () => Promise<T>, deps: unknown[] = []) {
  const [data, setData] = useState<T>();
  const [error, setError] = useState<Error>();
  const latest = useRef(request); latest.current = request;
  const reload = useCallback(() => financialChanged(), []);
  useEffect(() => {
    let alive = true, sequence = 0;
    setData(undefined); setError(undefined);
    const refresh = async () => {
      const current = ++sequence;
      try { const value = await latest.current(); if (alive && current === sequence) { setData(value); setError(undefined); } }
      catch (e) { if (alive && current === sequence) setError(e instanceof Error ? e : new Error('Unable to load financial information.')); }
    };
    const visibleRefresh = () => { if (!document.hidden) void refresh(); };
    void refresh();
    const timer = window.setInterval(visibleRefresh, 30_000);
    window.addEventListener('focus', visibleRefresh);
    window.addEventListener('financial-changed', visibleRefresh);
    document.addEventListener('visibilitychange', visibleRefresh);
    return () => { alive = false; clearInterval(timer); window.removeEventListener('focus', visibleRefresh); window.removeEventListener('financial-changed', visibleRefresh); document.removeEventListener('visibilitychange', visibleRefresh); };
  }, deps);
  return { data, error, reload, loading: !data && !error };
}
/** Reuse a key after uncertain network failures; a new user intent gets a new key. */
export function usePaymentAction() {
  const intent = useRef<{ signature: string; key: string }>();
  const busy = useRef(false);
  const [pending, setPending] = useState(false);
  const [error, setError] = useState<string>();
  const run = async <T,>(signature: string, action: (key: string) => Promise<T>): Promise<T | undefined> => {
    if (busy.current) return;
    if (intent.current?.signature !== signature) intent.current = { signature, key: crypto.randomUUID() };
    busy.current = true; setPending(true); setError(undefined);
    try { const result = await action(intent.current.key); intent.current = undefined; financialChanged(); return result; }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to complete payment.'); if (e instanceof ApiError && e.status < 500) intent.current = undefined; return undefined; }
    finally { busy.current = false; setPending(false); }
  };
  return { run, pending, error };
}

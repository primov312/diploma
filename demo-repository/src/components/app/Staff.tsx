// AI-assisted (Claude), 2026; reviewed by the author.
import type { ReactNode } from 'react';
import { cn } from '../../utils/cn';
import { formatCurrency, formatUsd } from '../../utils/format';

/** Small building blocks shared by the seller and administrator screens. */

/** HUF as everywhere in the app, with the stored USD value underneath. */
export const Money = ({ usd }: { usd: number }) => <span className="whitespace-nowrap">
  {formatCurrency(usd)}<span className="block text-xs text-gray-500">{formatUsd(usd)}</span>
</span>;

/** A scrollable, keyboard-focusable table region; phones scroll the table instead of the page. */
export const DataTable = ({ caption, head, children }: { caption: string; head: ReactNode[]; children: ReactNode }) => (
  <div className="overflow-x-auto" role="region" aria-label={caption} tabIndex={0}>
    <table className="w-full text-left text-sm">
      <caption className="sr-only">{caption}</caption>
      <thead><tr className="border-b text-xs uppercase tracking-wide text-gray-500">{head.map((h, i) => <th key={i} scope="col" className="p-3">{h}</th>)}</tr></thead>
      <tbody className="divide-y divide-gray-100">{children}</tbody>
    </table>
  </div>
);

export const Td = ({ children, className }: { children: ReactNode; className?: string }) => <td className={cn('p-3 align-top', className)}>{children}</td>;

export const Tile = ({ label, value, hint }: { label: string; value: ReactNode; hint?: ReactNode }) => (
  <div className="rounded-lg border border-gray-200 bg-white p-4">
    <p className="text-sm text-gray-600">{label}</p>
    <p className="mt-1 text-2xl font-bold text-gray-900">{value}</p>
    {hint && <p className="mt-1 text-xs text-gray-500">{hint}</p>}
  </div>
);

const TONES: Record<string, string> = {
  ACTIVE: 'border-success-100 bg-success-50 text-success-700', UP: 'border-success-100 bg-success-50 text-success-700',
  APPROVED: 'border-success-100 bg-success-50 text-success-700', ENABLED: 'border-success-100 bg-success-50 text-success-700',
  REJECTED: 'border-error-100 bg-error-50 text-error-700', DOWN: 'border-error-100 bg-error-50 text-error-700',
  DISABLED: 'border-error-100 bg-error-50 text-error-700', INACTIVE: 'border-gray-200 bg-gray-50 text-gray-600',
};
export const Pill = ({ value, label }: { value: string; label?: string }) => (
  <span className={cn('inline-flex rounded-md border px-2 py-0.5 text-xs font-semibold', TONES[value] ?? 'border-gray-200 bg-gray-50 text-gray-700')}>
    {label ?? value.replaceAll('_', ' ').toLowerCase()}
  </span>
);

export const Pager = ({ page, size, total, onPage }: { page: number; size: number; total: number; onPage: (page: number) => void }) => {
  const pages = Math.max(1, Math.ceil(total / size));
  return <nav aria-label="Pages" className="mt-4 flex flex-wrap items-center justify-between gap-3 text-sm text-gray-600">
    <span>{total} result{total === 1 ? '' : 's'} · page {page + 1} of {pages}</span>
    <span className="flex gap-2">
      <button type="button" className="btn-secondary px-3 text-sm" disabled={page === 0} onClick={() => onPage(page - 1)}>Previous</button>
      <button type="button" className="btn-secondary px-3 text-sm" disabled={page + 1 >= pages} onClick={() => onPage(page + 1)}>Next</button>
    </span>
  </nav>;
};

export const dateTime = (iso: string) => new Date(iso).toLocaleString();
export const termText = (unit: 'WEEK' | 'MONTH', count: number) => `${count} ${unit === 'WEEK' ? 'week' : 'month'}${count === 1 ? '' : 's'}`;

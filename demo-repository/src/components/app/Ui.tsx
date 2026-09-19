import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { DecisionStatus } from '../../api/types';
import { cn } from '../../utils/cn';
import { STATUS_TEXT } from '../../utils/reasons';

export const Page = ({ title, subtitle, actions, children }: { title: string; subtitle?: ReactNode; actions?: ReactNode; children: ReactNode }) => (
  <div className="bg-gradient-hero py-10 lg:py-14">
    <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
      <div className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-3xl font-bold text-gray-800">{title}</h1>
          {subtitle && <p className="mt-1 text-gray-600">{subtitle}</p>}
        </div>
        {actions && <div className="flex flex-wrap gap-3">{actions}</div>}
      </div>
      {children}
    </div>
  </div>
);

export const Card = ({ title, children, className, footer }: { title?: ReactNode; children: ReactNode; className?: string; footer?: ReactNode }) => (
  <section className={cn('rounded-2xl bg-white p-6 shadow-card', className)}>
    {title && <h2 className="mb-4 text-lg font-semibold text-gray-800">{title}</h2>}
    {children}
    {footer && <div className="mt-4 border-t border-gray-100 pt-4 text-sm">{footer}</div>}
  </section>
);

export const Notice = ({ tone = 'info', children }: { tone?: 'info' | 'error' | 'warning'; children: ReactNode }) => (
  <p
    role={tone === 'error' ? 'alert' : 'status'}
    className={cn(
      'rounded-lg border px-4 py-3 text-sm',
      tone === 'error' && 'border-error-100 bg-error-50 text-error-700',
      tone === 'warning' && 'border-warning-100 bg-warning-50 text-warning-700',
      tone === 'info' && 'border-secondary-200 bg-secondary-50 text-secondary-700',
    )}
  >
    {children}
  </p>
);

export const Loading = ({ label = 'Loading…' }: { label?: string }) => (
  <p className="py-8 text-center text-gray-500" role="status" aria-live="polite">
    {label}
  </p>
);

export const Empty = ({ children }: { children: ReactNode }) => (
  <p className="rounded-lg border border-dashed border-gray-200 px-4 py-8 text-center text-gray-500">{children}</p>
);

export const StatusBadge = ({ status }: { status: DecisionStatus }) => (
  <span className={cn('inline-flex items-center rounded-full border px-3 py-1 text-xs font-semibold', STATUS_TEXT[status].tone)}>
    {STATUS_TEXT[status].label}
  </span>
);

export const SyntheticTag = ({ children = 'Synthetic demo data' }: { children?: ReactNode }) => (
  <span className="inline-flex items-center rounded-full bg-secondary-100 px-2.5 py-0.5 text-xs font-medium text-secondary-700">
    {children}
  </span>
);

export const LinkButton = ({ to, children, variant = 'gradient', className }: { to: string; children: ReactNode; variant?: 'gradient' | 'secondary'; className?: string }) => (
  <Link to={to} className={cn(variant === 'gradient' ? 'btn-gradient' : 'btn-secondary', 'inline-flex items-center justify-center px-4 py-2 text-sm', className)}>
    {children}
  </Link>
);

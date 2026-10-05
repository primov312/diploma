import type { ReactNode } from 'react';
import { Link } from 'react-router-dom';
import type { DecisionStatus } from '../../api/types';
import { cn } from '../../utils/cn';
import { Icon, type IconName } from '../common/Icon';
import { STATUS_TEXT } from '../../utils/reasons';

export const Page = ({ title, subtitle, actions, children }: { title: string; subtitle?: ReactNode; actions?: ReactNode; children: ReactNode }) => (
  <div className="page-shell">
    <div className="w-full">
      <div className="mb-6 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <h1 className="text-2xl font-bold sm:text-3xl text-gray-800">{title}</h1>
          {subtitle && <p className="mt-1 max-w-3xl text-sm text-gray-600">{subtitle}</p>}
        </div>
        {actions && <div className="flex flex-wrap gap-3">{actions}</div>}
      </div>
      {children}
    </div>
  </div>
);

export const Card = ({ title, children, className, footer, icon }: { title?: ReactNode; children: ReactNode; className?: string; footer?: ReactNode; icon?: IconName }) => (
  <section className={cn('rounded-lg border border-gray-200 bg-white p-4 md:p-6', className)}>
    {title && <h2 className="mb-4 flex items-center gap-2 text-lg font-semibold text-gray-800">{icon && <Icon name={icon} />}{title}</h2>}
    {children}
    {footer && <div className="mt-4 border-t border-gray-100 pt-4 text-sm">{footer}</div>}
  </section>
);

export const Notice = ({ tone = 'info', children }: { tone?: 'info' | 'error' | 'warning'; children: ReactNode }) => (
  <p
    role={tone === 'error' ? 'alert' : 'status'}
    className={cn(
      'flex items-start gap-2 rounded-md border px-4 py-3 text-sm',
      tone === 'error' && 'border-error-100 bg-error-50 text-error-700',
      tone === 'warning' && 'border-warning-100 bg-warning-50 text-warning-700',
      tone === 'info' && 'border-secondary-200 bg-secondary-50 text-secondary-700',
    )}
  >
    <Icon name={tone === 'info' ? 'info' : tone === 'error' ? 'close' : 'clock'} /><span>{children}</span>
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
  <span className={cn('inline-flex items-center gap-1.5 rounded-md border px-3 py-1 text-xs font-semibold', STATUS_TEXT[status].tone)}>
    <Icon name={status === 'APPROVED' ? 'check' : status === 'REJECTED' ? 'close' : 'clock'} className="h-4 w-4" />{STATUS_TEXT[status].label}
  </span>
);

export const SyntheticTag = ({ children = 'Synthetic demo data' }: { children?: ReactNode }) => (
  <span className="inline-flex items-center gap-1 rounded border border-gray-200 bg-secondary-50 px-2.5 py-0.5 text-xs font-medium text-secondary-700">
    <Icon name="info" className="h-3.5 w-3.5" />{children}
  </span>
);

export const LinkButton = ({ to, children, variant = 'primary', className, icon = 'arrow' }: { to: string; children: ReactNode; variant?: 'primary' | 'secondary'; className?: string; icon?: IconName }) => (
  <Link to={to} className={cn(variant === 'primary' ? 'btn-primary' : 'btn-secondary', 'inline-flex items-center justify-center px-4 py-2 text-sm', className)}>
    <Icon name={icon} />{children}
  </Link>
);

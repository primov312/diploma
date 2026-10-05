import type { FormEvent, ReactNode } from 'react';
import { Icon } from '../../components/common/Icon';

type Props = {
  title: string;
  subtitle: string;
  error?: string | null;
  submitting: boolean;
  submitLabel: string;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
  children: ReactNode;
  footer: ReactNode;
};

export const AuthForm = ({ title, subtitle, error, submitting, submitLabel, onSubmit, children, footer }: Props) => (
  <div className="page-shell">
    <div className="grid overflow-hidden rounded-lg border border-gray-200 bg-white lg:min-h-[calc(100dvh-8rem)] lg:grid-cols-[1fr_1.15fr]">
      <aside className="flex flex-col justify-between gap-6 bg-primary p-6 text-white md:p-10">
        <div><p className="eyebrow text-primary-200">Your Rocket Credit account</p><h2 className="mt-4 max-w-md text-3xl font-semibold md:text-4xl">Keep your next decision in view.</h2><p className="mt-4 max-w-md text-primary-100">Estimates, purchases and financing requests in one place.</p></div>
        <ul className="grid gap-3 text-sm text-primary-100 sm:grid-cols-3 lg:grid-cols-1"><li className="flex items-center gap-2"><Icon name="chart" />Affordable amount</li><li className="flex items-center gap-2"><Icon name="store" />Demo catalogs</li><li className="flex items-center gap-2"><Icon name="file" />Saved decisions</li></ul>
        <p className="text-xs text-primary-200">Demo only. No real lending or payments.</p>
      </aside>
      <div className="flex items-center p-6 md:p-10">
        <div className="w-full lg:max-w-xl">
          <h1 className="text-2xl font-bold text-gray-800">{title}</h1>
          <p className="mt-1 text-sm text-gray-600">{subtitle}</p>
          <form className="mt-6 space-y-4" onSubmit={onSubmit} noValidate>
            {children}
            {error && <p className="rounded-md bg-error-50 px-3 py-2 text-sm text-error-700" role="alert">{error}</p>}
            <button type="submit" className="btn-primary w-full" disabled={submitting}><Icon name="arrow" />{submitting ? 'Please wait…' : submitLabel}</button>
          </form>
          <div className="mt-6 text-sm text-gray-600">{footer}</div>
        </div>
      </div>
    </div>
  </div>
);

type FieldProps = {
  id: string;
  label: string;
  type: string;
  value: string;
  onChange: (value: string) => void;
  autoComplete?: string;
  error?: string;
  hint?: string;
};

export const Field = ({ id, label, type, value, onChange, autoComplete, error, hint }: FieldProps) => (
  <div>
    <label htmlFor={id} className="block text-sm font-medium text-gray-700">
      {label}
    </label>
    <input
      id={id}
      name={id}
      type={type}
      value={value}
      autoComplete={autoComplete}
      required
      aria-invalid={Boolean(error)}
      aria-describedby={error ? `${id}-error` : hint ? `${id}-hint` : undefined}
      onChange={(e) => onChange(e.target.value)}
      className="form-input mt-1 w-full"
    />
    {error ? (
      <p id={`${id}-error`} className="mt-1 text-xs text-error-700">
        {error}
      </p>
    ) : hint ? (
      <p id={`${id}-hint`} className="mt-1 text-xs text-gray-500">
        {hint}
      </p>
    ) : null}
  </div>
);

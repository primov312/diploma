import type { FormEvent, ReactNode } from 'react';

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
  <div className="bg-gradient-hero py-16 lg:py-24">
    <div className="mx-auto max-w-md px-4 sm:px-6">
      <div className="rounded-2xl bg-white p-8 shadow-card">
        <h1 className="text-2xl font-bold text-gray-800">{title}</h1>
        <p className="mt-1 text-sm text-gray-600">{subtitle}</p>

        <form className="mt-6 space-y-4" onSubmit={onSubmit} noValidate>
          {children}
          {error && (
            <p className="rounded-lg bg-error-50 px-3 py-2 text-sm text-error-700" role="alert">
              {error}
            </p>
          )}
          <button type="submit" className="btn-gradient w-full py-2.5" disabled={submitting}>
            {submitting ? 'Please wait…' : submitLabel}
          </button>
        </form>

        <div className="mt-6 text-center text-sm text-gray-600">{footer}</div>
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
      className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2 text-gray-800 shadow-sm focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/30"
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

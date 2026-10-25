import { useState } from 'react';
import { Link } from 'react-router-dom';
import { cn } from '../utils/cn';
import { formatHuf } from '../utils/format';

/** Public entry point: a rough monthly figure here, the exact quote after sign-in. */
export default function PaymentCalculator({ className }: { className?: string }) {
  const [amount, setAmount] = useState('36000');
  const [months, setMonths] = useState(6);
  const perMonth = Number(amount) > 0 ? Number(amount) / months : 0;
  return <section className={cn('rounded-lg border border-gray-200 bg-white p-6', className)}>
    <h2 className="text-2xl font-semibold text-gray-800">Plan your payments</h2>
    <label className="mt-5 block text-sm font-medium">Purchase (HUF)<input className="form-input mt-2 w-full" type="number" min="2" step="1" value={amount} onChange={e => setAmount(e.target.value)} /></label>
    <fieldset className="mt-4"><legend className="text-sm font-medium">Months</legend>
      <div className="mt-2 flex flex-wrap gap-2">{[3, 6, 12, 24].map(n => <button key={n} type="button" aria-pressed={months === n} onClick={() => setMonths(n)}
        className={cn('min-h-11 min-w-14 rounded-full border px-4 text-sm font-medium', months === n ? 'border-primary bg-primary text-white' : 'border-gray-300 bg-white hover:border-primary')}>{n}</button>)}</div>
    </fieldset>
    <p className="mt-5 text-sm text-gray-600">About <strong className="text-2xl text-gray-900">{formatHuf(Math.round(perMonth))}</strong> / month + interest</p>
    <Link className="btn-primary mt-5 w-full" to={`/apply?partner=markethub&amount=${encodeURIComponent(amount)}`}>See my exact offer</Link>
  </section>;
}

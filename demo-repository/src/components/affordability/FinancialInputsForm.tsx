import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { meApi } from '../../api/rocket';
import type { ExpenseMode, FinancialInputs, HousingSituation, SaveFinancialInputs } from '../../api/types';
import { errorMessage, useApi } from '../../hooks/useApi';
import { Card, Loading, Notice } from '../app/Ui';

type AmountKey = 'monthlyNetIncome' | 'housingCost' | 'groceriesCost' | 'utilitiesCost' | 'transportCost' | 'otherLivingCosts' | 'legacyLivingExpenses' | 'monthlyObligations';
const amountFields: { key: AmountKey; label: string }[] = [
  { key: 'monthlyNetIncome', label: 'Monthly net income' },
  { key: 'housingCost', label: 'Housing' },
  { key: 'groceriesCost', label: 'Groceries' },
  { key: 'utilitiesCost', label: 'Utilities' },
  { key: 'transportCost', label: 'Transport' },
  { key: 'otherLivingCosts', label: 'Other living costs' },
  { key: 'legacyLivingExpenses', label: 'Aggregate living expenses' },
  { key: 'monthlyObligations', label: 'Existing monthly debt payments' },
];
const money = (value: number | null) => value == null ? '' : value.toFixed(2);
const parse = (value: string): number | null => value.trim() === '' ? null : Number(value);

export function FinancialInputsForm({ onSaved }: { onSaved: () => void }) {
  const loaded = useApi(() => meApi.financialInputs(), []);
  const [revision, setRevision] = useState(1);
  const [values, setValues] = useState<Record<AmountKey, string>>({
    monthlyNetIncome: '', housingCost: '', groceriesCost: '', utilitiesCost: '', transportCost: '',
    otherLivingCosts: '', legacyLivingExpenses: '', monthlyObligations: '',
  });
  const [housing, setHousing] = useState<HousingSituation>('OTHER');
  const [mode, setMode] = useState<ExpenseMode>('AGGREGATE');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');

  useEffect(() => {
    if (loaded.status !== 'ready') return;
    const i: FinancialInputs = loaded.data;
    setRevision(i.revision);
    setMode(i.expenseMode);
    setHousing(i.housingSituation);
    setValues({
      monthlyNetIncome: money(i.monthlyNetIncome), housingCost: money(i.housingCost),
      groceriesCost: money(i.groceriesCost), utilitiesCost: money(i.utilitiesCost),
      transportCost: money(i.transportCost), otherLivingCosts: money(i.otherLivingCosts),
      legacyLivingExpenses: money(i.legacyLivingExpenses), monthlyObligations: money(i.monthlyObligations),
    });
  }, [loaded.status, loaded.status === 'ready' ? loaded.data.revision : null]);

  const setAmount = (key: AmountKey, value: string) => setValues((current) => ({ ...current, [key]: value }));
  const field = (key: AmountKey, label: string, required: boolean) => (
    <label key={key} className="block text-sm font-medium text-gray-700">
      {label}{required && <span aria-hidden="true"> *</span>}
      <div className="mt-1 flex rounded-lg border border-gray-300 focus-within:border-primary focus-within:ring-2 focus-within:ring-primary-100">
        <span className="px-3 py-2 text-gray-500">$</span>
        <input type="number" min="0" step="0.01" inputMode="decimal" value={values[key]}
          onChange={(event) => setAmount(key, event.target.value)} required={required}
          className="w-full rounded-r-lg border-0 bg-transparent px-2 py-2 focus:outline-none focus:ring-0" />
      </div>
    </label>
  );

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(''); setNotice(''); setSaving(true);
    const decimal = Object.fromEntries(amountFields.map(({ key }) => [key, parse(values[key])])) as Record<AmountKey, number | null>;
    const invalidAmount = amountFields.some(({ key }) => {
      const raw = values[key].trim();
      return raw !== '' && (!/^\d{1,10}(\.\d{1,2})?$/.test(raw) || Number(raw) > 9999999999.99);
    });
    if (invalidAmount) {
      setError('Enter non-negative amounts with up to two decimal places.'); setSaving(false); return;
    }
    const body: SaveFinancialInputs = {
      expectedRevision: revision, monthlyNetIncome: decimal.monthlyNetIncome,
      housingSituation: housing, expenseMode: mode,
      housingCost: mode === 'ITEMIZED' ? decimal.housingCost : null,
      groceriesCost: mode === 'ITEMIZED' ? decimal.groceriesCost : null,
      utilitiesCost: mode === 'ITEMIZED' ? decimal.utilitiesCost : null,
      transportCost: mode === 'ITEMIZED' ? decimal.transportCost : null,
      otherLivingCosts: mode === 'ITEMIZED' ? decimal.otherLivingCosts : null,
      legacyLivingExpenses: mode === 'AGGREGATE' ? decimal.legacyLivingExpenses : null,
      monthlyObligations: decimal.monthlyObligations ?? 0,
    };
    try {
      const result = await meApi.saveFinancialInputs(body);
      setRevision(result.inputs.revision);
      setNotice('Saved. Your estimate is recalculating.');
      onSaved();
    } catch (cause) {
      setError(cause instanceof ApiError && cause.code === 'REVISION_CONFLICT'
        ? 'These inputs changed in another session. Reload the latest values before saving again.'
        : errorMessage(cause instanceof Error ? cause : new Error(String(cause))));
    } finally { setSaving(false); }
  };

  return <Card title="Financial information">
    {loaded.status === 'loading' && <Loading label="Loading saved inputs…" />}
    {loaded.status === 'error' && <Notice tone="error">{errorMessage(loaded.error)}</Notice>}
    {loaded.status === 'ready' && <form onSubmit={submit} className="space-y-5">
      <p className="text-sm text-gray-600">Amounts are monthly USD. Income is declared information and is not independently verified.</p>
      <div className="grid gap-4 sm:grid-cols-2">
        {field('monthlyNetIncome', 'Net income', false)}
        {field('monthlyObligations', 'Existing debt payments', true)}
      </div>
      <div className="grid gap-4 sm:grid-cols-2">
        <label className="text-sm font-medium text-gray-700">Housing situation
          <select value={housing} onChange={(e) => setHousing(e.target.value as HousingSituation)} className="input mt-1 w-full">
            <option value="RENTING">Renting</option><option value="OWNER">Owner</option>
            <option value="FAMILY">Living with family</option><option value="OTHER">Other</option>
          </select>
        </label>
        <label className="text-sm font-medium text-gray-700">Expense mode
          <select value={mode} onChange={(e) => setMode(e.target.value as ExpenseMode)} className="input mt-1 w-full">
            <option value="AGGREGATE">Aggregate expenses</option><option value="ITEMIZED">Itemized expenses</option>
          </select>
        </label>
      </div>
      {mode === 'AGGREGATE' ? <div className="max-w-sm">{field('legacyLivingExpenses', 'Aggregate monthly living expenses', true)}</div> :
        <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">{amountFields.slice(1, 6).map(({ key, label }) => field(key, label, true))}</div>}
      <p className="text-xs text-gray-500">Source for these saved values: <strong>{loaded.data.source}</strong>. Saving edits labels the new revision USER_DECLARED.</p>
      {error && <Notice tone="error">{error}</Notice>}{notice && <Notice>{notice}</Notice>}
      <button type="submit" disabled={saving} className="btn-gradient px-5 py-2 text-sm disabled:opacity-60">{saving ? 'Saving…' : 'Save financial information'}</button>
    </form>}
  </Card>;
}

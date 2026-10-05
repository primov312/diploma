import { useEffect, useState, type FormEvent } from 'react';
import { meApi } from '../../api/rocket';
import type { HousingSituation } from '../../api/types';
import { formatCurrency } from '../../utils/format';
import { errorMessage, useApi } from '../../hooks/useApi';
import { Card, Loading, Notice, SyntheticTag } from '../app/Ui';
import LocalDropdown from '../common/LocalDropdown';

export function FinancialInputsForm({ onSaved }: { onSaved: () => void }) {
  const loaded = useApi(() => meApi.financialInputs(), []);
  const [housing, setHousing] = useState<HousingSituation>('OTHER');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  useEffect(() => { if (loaded.status === 'ready') setHousing(loaded.data.housingSituation); }, [loaded.status, loaded.data?.revision]);
  const submit = async (event: FormEvent) => {
    event.preventDefault(); if (loaded.status !== 'ready') return;
    setSaving(true); setError(''); setNotice('');
    try {
      await meApi.saveFinancialInputs({ expectedRevision: loaded.data.revision, housingSituation: housing, expenseMode: 'AUTOMATIC' });
      setNotice('Housing situation saved. Automatic costs are recalculating.'); loaded.reload(); onSaved();
    } catch (cause) { setError(errorMessage(cause instanceof Error ? cause : new Error(String(cause)))); }
    finally { setSaving(false); }
  };
  return <Card title="Financial information" icon="wallet">
    {loaded.status === 'loading' && <Loading label="Loading financial information…" />}
    {loaded.status === 'error' && <Notice tone="error">{errorMessage(loaded.error)}</Notice>}
    {loaded.status === 'ready' && <form onSubmit={submit} className="grid gap-6 xl:grid-cols-[minmax(16rem,0.65fr)_minmax(0,1.35fr)]">
      <div className="space-y-4">
        <p className="text-sm text-gray-600">Save your housing situation to update automatic living costs.</p>
        <div><label htmlFor="housing-situation" className="block text-sm font-medium text-gray-700">Housing situation</label>
          <LocalDropdown id="housing-situation" value={housing} onValueChange={value => setHousing(value as HousingSituation)} className="mt-1 w-full"
            options={[{ value: 'RENTING', label: 'Renting' }, { value: 'OWNER', label: 'Owner' }, { value: 'FAMILY', label: 'Living with family' }, { value: 'OTHER', label: 'Other' }]} />
        </div>
        {error && <Notice tone="error">{error}</Notice>}{notice && <Notice>{notice}</Notice>}
        <button type="submit" disabled={saving} className="btn-primary text-sm">{saving ? 'Saving…' : 'Save housing situation'}</button>
      </div>
      <div className="space-y-4">
        <SyntheticTag />
        <dl className="grid gap-4 sm:grid-cols-2">
          <div className="border-t border-gray-200 pt-3"><dt className="text-sm text-gray-600">Monthly net income</dt><dd className="mt-1 text-2xl font-semibold">{loaded.data.monthlyNetIncome == null ? 'Unavailable' : formatCurrency(loaded.data.monthlyNetIncome)}</dd></div>
          <div className="border-t border-gray-200 pt-3"><dt className="text-sm text-gray-600">Existing monthly debt payments</dt><dd className="mt-1 text-2xl font-semibold">{formatCurrency(loaded.data.monthlyObligations)}</dd></div>
        </dl>
        <details><summary>How living expenses are calculated</summary><p>Income and debt come from the supplied demo profile. Living costs use researched prices and synthetic location history in Address &amp; costs. The supplied expense baseline remains a minimum. Verify your address to apply researched references; until then, the estimate uses that baseline.</p></details>
      </div>
    </form>}
  </Card>;
}

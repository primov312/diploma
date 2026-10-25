import { meApi } from '../../api/rocket';
import { formatCurrency } from '../../utils/format';
import { errorMessage, useApi } from '../../hooks/useApi';
import { Card, Loading, Notice } from '../app/Ui';
import { HOUSING_LABELS } from './housing';

/** Read-only summary. The housing situation is changed, with a supporting document, in "Address and living costs". */
export function IncomeAndHousingCard({ refreshKey }: { refreshKey: number }) {
  const loaded = useApi(() => meApi.financialInputs(), [refreshKey]);
  const housing = useApi(() => meApi.housing(), [refreshKey]);
  return <Card title="Income and housing" icon="wallet">
    {loaded.status === 'loading' && <Loading label="Loading financial information…" />}
    {loaded.status === 'error' && <Notice tone="error">{errorMessage(loaded.error)}</Notice>}
    {loaded.status === 'ready' && <div className="space-y-4">
      <dl className="grid gap-4 sm:grid-cols-3">
        <div className="border-t border-gray-200 pt-3"><dt className="text-sm text-gray-600">Housing situation</dt>
          <dd className="mt-1 text-2xl font-semibold">{HOUSING_LABELS[loaded.data.housingSituation]}</dd>
          {housing.status === 'ready' && <dd className="mt-1 text-xs text-gray-500">{housing.data.verified ? 'Verified with a document' : 'Not verified by a document'}</dd>}</div>
        <div className="border-t border-gray-200 pt-3"><dt className="text-sm text-gray-600">Monthly net income</dt><dd className="mt-1 text-2xl font-semibold">{loaded.data.monthlyNetIncome == null ? 'Unavailable' : formatCurrency(loaded.data.monthlyNetIncome)}</dd></div>
        <div className="border-t border-gray-200 pt-3"><dt className="text-sm text-gray-600">Other monthly debt payments</dt><dd className="mt-1 text-2xl font-semibold">{formatCurrency(loaded.data.monthlyObligations)}</dd></div>
      </dl>
      <p className="text-xs text-gray-500">Income and debts come from your demo profile. Living costs come from your address below. To change your housing situation, use <strong>Edit</strong> in Address and living costs.</p>
    </div>}
  </Card>;
}

// AI-assisted (Claude), 2026; reviewed by the author.
import { Link, useSearchParams } from 'react-router-dom';
import { adminApi } from '../../api/staff';
import { Card, Empty, Loading, Notice, Page } from '../../components/app/Ui';
import { DataTable, Money, Pill, Td, dateTime } from '../../components/app/Staff';
import { errorMessage, useApi } from '../../hooks/useApi';
import { cn } from '../../utils/cn';

/** Requests the automatic check left in REVIEW. Deciding them is the human intervention step (GDPR Art. 22(3)). */
const AdminReviewsPage = () => {
  const [params, setParams] = useSearchParams();
  const state = params.get('state') === 'DECIDED' ? 'DECIDED' : 'OPEN';
  const list = useApi(() => adminApi.reviews(state), [state]);
  return <Page title="Reviews" subtitle="Credit requests the automatic check could not decide. A person decides each one once; the automatic result stays unchanged.">
    <div className="mb-4 flex gap-2" role="group" aria-label="Review state">
      {(['OPEN', 'DECIDED'] as const).map((s) => <button key={s} type="button" aria-pressed={state === s} onClick={() => setParams(s === 'OPEN' ? {} : { state: s })}
        className={cn('min-h-11 rounded-full border px-4 text-sm font-medium', state === s ? 'border-primary bg-primary text-white' : 'border-gray-300 bg-white text-gray-700')}>
        {s === 'OPEN' ? 'Open' : 'Decided'}</button>)}
    </div>
    <Card>
      {list.status === 'loading' && <Loading />}
      {list.status === 'error' && <Notice tone="error">{errorMessage(list.error)}</Notice>}
      {list.status === 'ready' && (list.data.items.length === 0 ? <Empty>{state === 'OPEN' ? 'No requests are waiting for review.' : 'No reviews decided yet.'}</Empty> :
        <DataTable caption={state === 'OPEN' ? 'Open reviews, oldest first' : 'Decided reviews, newest first'}
          head={['Request', 'Store', 'Requested', 'Possible', state === 'OPEN' ? 'Received' : 'Outcome', '']}>
          {list.data.items.map((r) => <tr key={r.applicationId}>
            <Td><span className="font-medium">#{r.applicationId}</span><span className="block text-xs text-gray-500">{r.productName ?? 'Custom amount'} · customer #{r.customerId}</span></Td>
            <Td>{r.partnerName}</Td>
            <Td><Money usd={r.requestedAmount} /></Td>
            <Td><Money usd={r.possibleAmount} /></Td>
            <Td>{state === 'OPEN' ? dateTime(r.createdAt) : <Pill value={r.outcome ?? ''} />}</Td>
            <Td><Link to={`/admin/reviews/${r.applicationId}`} className="btn-secondary px-3 text-sm" aria-label={`Open request ${r.applicationId}`}>
              {state === 'OPEN' ? 'Review' : 'View'}</Link></Td>
          </tr>)}
        </DataTable>)}
    </Card>
  </Page>;
};

export default AdminReviewsPage;

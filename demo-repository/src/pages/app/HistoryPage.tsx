import { useState } from 'react';
import { partnersApi, transactionsApi } from '../../api/rocket';
import { Card, Empty, Loading, Notice, Page, SyntheticTag } from '../../components/app/Ui';
import { errorMessage, useApi } from '../../hooks/useApi';
import { formatCurrency } from '../../utils/format';

const HistoryPage = () => {
  const [partner, setPartner] = useState<string>('');
  const partners = useApi(() => partnersApi.list(), []);
  const list = useApi(() => transactionsApi.list(partner || undefined), [partner]);

  return (
    <Page title="Purchase history" subtitle="Seeded purchases at the three demo stores. This is history, not credit.">
      <Card>
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <label htmlFor="partner" className="text-sm font-medium text-gray-700">Store</label>
          <select
            id="partner"
            value={partner}
            onChange={(e) => setPartner(e.target.value)}
            className="form-input py-2"
          >
            <option value="">All stores</option>
            {partners.data?.map((p) => (
              <option key={p.slug} value={p.slug}>{p.displayName}</option>
            ))}
          </select>
          <SyntheticTag />
        </div>

        {list.status === 'loading' && <Loading />}
        {list.status === 'error' && <Notice tone="error">{errorMessage(list.error)}</Notice>}
        {list.status === 'ready' && list.data.length === 0 && <Empty>No purchases for this selection.</Empty>}
        {list.status === 'ready' && list.data.length > 0 && (
          <div className="overflow-x-auto">
            <table className="w-full text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wide text-gray-500">
                  <th className="py-2 pr-4">Date</th>
                  <th className="py-2 pr-4">Store</th>
                  <th className="py-2 pr-4">Description</th>
                  <th className="py-2 pr-4 text-right">Amount</th>
                  <th className="py-2 pr-4">Status</th>
                  <th className="py-2">Paid on time</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {list.data.map((t) => (
                  <tr key={t.id}>
                    <td className="py-2 pr-4 whitespace-nowrap">{t.occurredOn}</td>
                    <td className="py-2 pr-4">{t.partnerName}</td>
                    <td className="py-2 pr-4 text-gray-600">{t.description}</td>
                    <td className="py-2 pr-4 text-right font-medium">{formatCurrency(t.amount)}</td>
                    <td className="py-2 pr-4">{t.status === 'REFUNDED' ? 'Refunded' : 'Completed'}</td>
                    <td className="py-2">{t.paidOnTime ? 'yes' : 'no'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </Card>
    </Page>
  );
};

export default HistoryPage;

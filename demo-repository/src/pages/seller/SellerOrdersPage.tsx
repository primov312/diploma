// AI-assisted (Claude), 2026; reviewed by the author.
import { useState } from 'react';
import { sellerApi } from '../../api/staff';
import { Card, Empty, Loading, Notice, Page } from '../../components/app/Ui';
import { DataTable, Money, Pager, Pill, Td, dateTime, termText } from '../../components/app/Staff';
import { errorMessage, useApi } from '../../hooks/useApi';

const SIZE = 20;

/** Financed orders of the seller's own partner. No customer name, e-mail or financial data is ever sent here. */
const SellerOrdersPage = () => {
  const [status, setStatus] = useState('');
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [page, setPage] = useState(0);
  const orders = useApi(() => sellerApi.orders({ status, from, to, page, size: SIZE }), [status, from, to, page]);
  const filter = (set: (v: string) => void) => (value: string) => { set(value); setPage(0); };

  return <Page title="Financed orders" subtitle="Each order is a credit Rocket Credit opened when a customer accepted an offer at your store.">
    <Card>
      <form className="mb-4 grid gap-3 sm:grid-cols-3" onSubmit={(e) => e.preventDefault()} aria-label="Filter orders">
        <label className="text-sm text-gray-700">Status
          <select className="form-input mt-1 w-full" value={status} onChange={(e) => filter(setStatus)(e.target.value)}>
            <option value="">All</option><option value="ACTIVE">Active</option><option value="PAID">Repaid</option><option value="SETTLED_EARLY">Settled early</option>
          </select>
        </label>
        <label className="text-sm text-gray-700">From<input type="date" className="form-input mt-1 w-full" value={from} onChange={(e) => filter(setFrom)(e.target.value)} /></label>
        <label className="text-sm text-gray-700">To<input type="date" className="form-input mt-1 w-full" value={to} onChange={(e) => filter(setTo)(e.target.value)} /></label>
      </form>
      {orders.status === 'loading' && <Loading />}
      {orders.status === 'error' && <Notice tone="error">{errorMessage(orders.error)}</Notice>}
      {orders.status === 'ready' && (orders.data.items.length === 0 ? <Empty>No financed orders match these filters.</Empty> : <>
        <DataTable caption="Financed orders" head={['Order', 'Customer', 'Product', 'Principal', 'Plan', 'Status', 'Date']}>
          {orders.data.items.map((o) => <tr key={o.orderRef}>
            <Td className="font-medium">#{o.orderRef}</Td>
            <Td><span className="font-mono">{o.customerRef}</span></Td>
            <Td>{o.productName ?? 'Custom amount'}</Td>
            <Td><Money usd={o.principal} /></Td>
            <Td>{termText(o.termUnit, o.installmentCount)}</Td>
            <Td><Pill value={o.loanStatus} /></Td>
            <Td className="whitespace-nowrap">{dateTime(o.createdAt)}</Td>
          </tr>)}
        </DataTable>
        <Pager page={page} size={SIZE} total={orders.data.total} onPage={setPage} />
      </>)}
      <p className="mt-4 text-xs text-gray-500">Customer references are pseudonyms: the same customer keeps the same reference at your store, and different stores see different references.</p>
    </Card>
  </Page>;
};

export default SellerOrdersPage;

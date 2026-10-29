// AI-assisted (Claude), 2026; reviewed by the author.
import { sellerApi } from '../../api/staff';
import { Card, LinkButton, Loading, Notice, Page } from '../../components/app/Ui';
import { DataTable, Money, Td, Tile } from '../../components/app/Staff';
import { errorMessage, useApi } from '../../hooks/useApi';
import { formatCurrency } from '../../utils/format';
import { STATUS_TEXT } from '../../utils/reasons';

/** The seller's own partner only; the server takes the partner from the session. */
const SellerOverviewPage = () => {
  const overview = useApi(() => sellerApi.overview(), []);
  if (overview.status === 'loading') return <Page title="Seller overview"><Loading /></Page>;
  if (overview.status === 'error') return <Page title="Seller overview"><Notice tone="error">{errorMessage(overview.error)}</Notice></Page>;
  const o = overview.data;
  const max = Math.max(1, ...o.last30Days.map((d) => d.orders));
  return <Page title={`${o.partner.name} overview`}
    subtitle="Credit requests and financed orders at your store. Customers appear only as anonymous references."
    actions={<><LinkButton to="/seller/orders" variant="secondary" icon="file">Orders</LinkButton><LinkButton to="/seller/catalog" icon="store">Catalogue</LinkButton></>}>
    {!o.partner.active && <div className="mb-4"><Notice tone="warning">An administrator has paused your store: your products are hidden from the marketplace.</Notice></div>}
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <Tile label="Financed orders" value={o.financedOrders} />
      <Tile label="Paid to you by Rocket Credit" value={<Money usd={o.financedPrincipal} />} hint="Simulated: the principal of every accepted credit" />
      <Tile label="Active credits" value={o.activeLoans} />
      <Tile label="Closed credits" value={o.closedLoans} hint="Repaid or settled early" />
    </div>
    <div className="mt-6 grid gap-6 lg:grid-cols-3">
      <Card title="Credit requests by decision" icon="file">
        <ul className="space-y-2 text-sm">{Object.entries(o.applicationsByDecision).map(([status, count]) =>
          <li key={status} className="flex justify-between gap-3"><span>{STATUS_TEXT[status as keyof typeof STATUS_TEXT]?.label ?? status}</span><strong className="tabular-nums">{count}</strong></li>)}</ul>
        <p className="mt-4 text-xs text-gray-500">Requests that cite your store. Decisions are made by Rocket Credit, not by you.</p>
      </Card>
      <Card title="Financed orders, last 30 days" icon="chart" className="lg:col-span-2">
        <div className="flex h-36 items-end gap-0.5" role="img" aria-label={`Financed orders per day over the last 30 days; ${o.last30Days.reduce((n, d) => n + d.orders, 0)} in total`}>
          {o.last30Days.map((d) => <div key={d.day} className="flex-1 rounded-t bg-accent" title={`${d.day}: ${d.orders}`}
            style={{ height: `${(d.orders / max) * 100}%`, minHeight: d.orders ? 4 : 1, opacity: d.orders ? 1 : 0.25 }} />)}
        </div>
        <details className="mt-4"><summary>Daily values</summary>
          <DataTable caption="Financed orders per day" head={['Day', 'Orders', 'Principal']}>
            {o.last30Days.filter((d) => d.orders > 0).map((d) => <tr key={d.day}><Td>{d.day}</Td><Td>{d.orders}</Td><Td>{formatCurrency(d.principal)}</Td></tr>)}
          </DataTable>
        </details>
      </Card>
    </div>
  </Page>;
};

export default SellerOverviewPage;

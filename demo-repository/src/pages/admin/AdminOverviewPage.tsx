// AI-assisted (Claude), 2026; reviewed by the author.
import { Link } from 'react-router-dom';
import { adminApi } from '../../api/staff';
import { Card, Loading, Notice, Page } from '../../components/app/Ui';
import { Pill, Tile } from '../../components/app/Staff';
import { errorMessage, useApi } from '../../hooks/useApi';
import { STATUS_TEXT } from '../../utils/reasons';

const ROLE_TITLE = { CUSTOMER: 'Customers', SELLER: 'Sellers', ADMIN: 'Administrators' } as const;

const AdminOverviewPage = () => {
  const overview = useApi(() => adminApi.overview(), []);
  if (overview.status === 'loading') return <Page title="Platform overview"><Loading /></Page>;
  if (overview.status === 'error') return <Page title="Platform overview"><Notice tone="error">{errorMessage(overview.error)}</Notice></Page>;
  const o = overview.data;
  return <Page title="Platform overview" subtitle="Accounts, decisions, credits and service health. Counts only; no customer data.">
    <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
      <Tile label="Open reviews" value={<Link to="/admin/reviews" className="underline decoration-dotted">{o.reviews.open}</Link>}
        hint={`${o.reviews.approved} approved · ${o.reviews.rejected} rejected after review`} />
      <Tile label="Active credits" value={o.activeLoans} />
      <Tile label="Overdue instalments" value={o.overdueInstallments} hint="Unpaid after the due date" />
      <Tile label="Credit requests" value={Object.values(o.applicationsByDecision).reduce((a, b) => a + b, 0)} />
    </div>
    <div className="mt-6 grid gap-6 lg:grid-cols-3">
      <Card title="Accounts" icon="user">
        <ul className="space-y-2 text-sm">{Object.entries(o.usersByRole).map(([role, counts]) => <li key={role} className="flex justify-between gap-3">
          <Link to={`/admin/users?role=${role}`} className="underline decoration-dotted">{ROLE_TITLE[role as keyof typeof ROLE_TITLE]}</Link>
          <span className="tabular-nums"><strong>{counts.enabled}</strong> enabled{counts.disabled ? ` · ${counts.disabled} disabled` : ''}</span></li>)}</ul>
      </Card>
      <Card title="Decisions" icon="file">
        <ul className="space-y-2 text-sm">{Object.entries(o.applicationsByDecision).map(([status, count]) => <li key={status} className="flex justify-between gap-3">
          <span>{STATUS_TEXT[status as keyof typeof STATUS_TEXT]?.label ?? status}</span><strong className="tabular-nums">{count}</strong></li>)}</ul>
      </Card>
      <Card title="Services" icon="activity">
        <ul className="space-y-2 text-sm">{Object.entries(o.services).map(([name, status]) => <li key={name} className="flex justify-between gap-3">
          <span className="capitalize">{name}</span><Pill value={status} label={status.toLowerCase()} /></li>)}</ul>
      </Card>
    </div>
  </Page>;
};

export default AdminOverviewPage;

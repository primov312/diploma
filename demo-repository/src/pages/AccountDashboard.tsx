import { useState } from 'react';
import { Link } from 'react-router-dom';
import { applicationsApi, meApi, transactionsApi } from '../api/rocket';
import { useAuth } from '../auth/AuthContext';
import { Card, Empty, LinkButton, Loading, Notice, Page, StatusBadge } from '../components/app/Ui';
import { AffordabilityDashboardCard } from '../components/affordability/AffordabilityDashboardCard';
import { FinancialInputsForm } from '../components/affordability/FinancialInputsForm';
import { AddressVerificationPanel } from '../components/affordability/AddressVerificationPanel';
import { DemoSignalsPanel } from '../components/affordability/DemoSignalsPanel';
import { errorMessage, useApi } from '../hooks/useApi';
import { formatCurrency } from '../utils/format';

/**
 * Everything shown here comes from the backend: the synthetic financial profile,
 * seeded purchase history and saved credit applications. Purchases and credit
 * applications are separate things and are shown in separate cards.
 */
const AccountDashboard = () => {
  const { user } = useAuth();
  const profile = useApi(() => meApi.profile(), []);
  const transactions = useApi(() => transactionsApi.list(), []);
  const applications = useApi(() => applicationsApi.list(), []);
  const [financialRefresh, setFinancialRefresh] = useState(0);

  const byPartner = transactions.data
    ? Object.values(
        transactions.data.reduce<Record<string, { name: string; count: number; total: number }>>((acc, t) => {
          const entry = (acc[t.partnerSlug] ??= { name: t.partnerName, count: 0, total: 0 });
          if (t.status === 'COMPLETED') {
            entry.count += 1;
            entry.total += t.amount;
          }
          return acc;
        }, {}),
      )
    : [];

  return (
    <Page
      title={`Hello, ${user?.displayName ?? ''}`}
      subtitle="Your affordable amount, editable financial information, purchases and applications."
      actions={
        <>
          <LinkButton to="/apply">Apply for financing</LinkButton>
          <LinkButton to="/stores" variant="secondary">Visit the stores</LinkButton>
        </>
      }
    >
      <div className="grid gap-6 lg:grid-cols-3">
        <AffordabilityDashboardCard refreshKey={financialRefresh} />
        <div className="lg:col-span-3">
          <FinancialInputsForm onSaved={() => setFinancialRefresh((value) => value + 1)} />
        </div>
        <AddressVerificationPanel onUpdated={() => setFinancialRefresh((value) => value + 1)} />
        <DemoSignalsPanel />
        <Card title="Account profile" className="lg:col-span-1">
          {profile.status === 'loading' && <Loading />}
          {profile.status === 'error' && <Notice tone="error">{errorMessage(profile.error)}</Notice>}
          {profile.status === 'ready' && <dl className="space-y-3 text-sm">
            <div className="flex justify-between"><dt className="text-gray-600">Account age</dt><dd className="font-medium">{profile.data.accountAgeMonths} months</dd></div>
            <div className="flex justify-between"><dt className="text-gray-600">Profile</dt><dd className="font-medium">{profile.data.profileComplete ? 'complete' : 'incomplete'}</dd></div>
            <div className="flex justify-between"><dt className="text-gray-600">Email</dt><dd className="font-medium">{profile.data.emailVerified ? 'verified' : 'not verified'}</dd></div>
          </dl>}
        </Card>

        <Card
          title="Purchase history by store"
          className="lg:col-span-2"
          footer={<Link to="/history" className="font-medium text-primary hover:text-primary-600">See all purchases →</Link>}
        >
          {transactions.status === 'loading' && <Loading />}
          {transactions.status === 'error' && <Notice tone="error">{errorMessage(transactions.error)}</Notice>}
          {transactions.status === 'ready' && byPartner.length === 0 && <Empty>No purchases yet.</Empty>}
          {transactions.status === 'ready' && byPartner.length > 0 && (
            <div className="grid gap-4 sm:grid-cols-3">
              {byPartner.map((p) => (
                <div key={p.name} className="rounded-xl border border-gray-100 p-4">
                  <div className="text-sm text-gray-600">{p.name}</div>
                  <div className="mt-1 text-2xl font-bold text-gray-800">{p.count}</div>
                  <div className="text-xs text-gray-500">completed purchases · {formatCurrency(p.total)}</div>
                </div>
              ))}
            </div>
          )}
          <p className="mt-4 text-xs text-gray-500">Purchases are seeded historical data from the demo stores. They are not credit applications.</p>
        </Card>

        <Card
          title="Credit applications"
          className="lg:col-span-3"
          footer={<Link to="/applications" className="font-medium text-primary hover:text-primary-600">All applications →</Link>}
        >
          {applications.status === 'loading' && <Loading />}
          {applications.status === 'error' && <Notice tone="error">{errorMessage(applications.error)}</Notice>}
          {applications.status === 'ready' && applications.data.length === 0 && (
            <Empty>
              You have not applied yet. <Link to="/apply" className="font-medium text-primary">Submit your first request</Link>.
            </Empty>
          )}
          {applications.status === 'ready' && applications.data.length > 0 && (
            <ul className="divide-y divide-gray-100">
              {applications.data.slice(0, 5).map((a) => (
                <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                  <div>
                    <Link to={`/applications/${a.id}`} className="font-medium text-gray-800 hover:text-primary">
                      {formatCurrency(a.requestedAmount)} at {a.partnerName}
                    </Link>
                    <div className="text-xs text-gray-500">{new Date(a.createdAt).toLocaleString()}</div>
                  </div>
                  <StatusBadge status={a.decisionStatus} />
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </Page>
  );
};

export default AccountDashboard;

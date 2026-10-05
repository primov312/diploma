import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { applicationsApi, meApi, transactionsApi } from '../api/rocket';
import { useAuth } from '../auth/AuthContext';
import { Card, Empty, LinkButton, Loading, Notice, Page, StatusBadge } from '../components/app/Ui';
import { AffordabilityDashboardCard } from '../components/affordability/AffordabilityDashboardCard';
import { FinancialInputsForm } from '../components/affordability/FinancialInputsForm';
import { AddressVerificationPanel } from '../components/affordability/AddressVerificationPanel';
import { DemoSignalsPanel } from '../components/affordability/DemoSignalsPanel';
import { errorMessage, useApi } from '../hooks/useApi';
import { Icon } from '../components/common/Icon';
import { formatCurrency } from '../utils/format';

const sections = [
  { id: 'overview', icon: 'chart', label: 'Overview' },
  { id: 'financial', icon: 'wallet', label: 'Finances' },
  { id: 'address', icon: 'pin', label: 'Address & costs' },
  { id: 'activity', icon: 'activity', label: 'Activity' },
  { id: 'profile', icon: 'user', label: 'Profile' },
  { id: 'purchases', icon: 'store', label: 'Purchases' },
  { id: 'applications', icon: 'file', label: 'Applications' },
] as const;

type DashboardSection = (typeof sections)[number]['id'];

const sectionUrl = (section: DashboardSection) =>
  section === 'overview' ? '/account-dashboard' : `/account-dashboard?section=${section}`;

const AccountProfileSection = () => {
  const profile = useApi(() => meApi.profile(), []);

  return <Card title="Account profile" icon="user">
    {profile.status === 'loading' && <Loading />}
    {profile.status === 'error' && <Notice tone="error">{errorMessage(profile.error)}</Notice>}
    {profile.status === 'ready' && <dl className="grid gap-4 sm:grid-cols-3 text-sm">
      <div className="flex flex-col gap-2 border-t border-gray-200 pt-3"><dt className="text-gray-600">Account age</dt><dd className="font-medium">{profile.data.accountAgeMonths} months</dd></div>
      <div className="flex flex-col gap-2 border-t border-gray-200 pt-3"><dt className="text-gray-600">Profile</dt><dd className="font-medium">{profile.data.profileComplete ? 'complete' : 'incomplete'}</dd></div>
      <div className="flex flex-col gap-2 border-t border-gray-200 pt-3"><dt className="text-gray-600">Email</dt><dd className="font-medium">{profile.data.emailVerified ? 'verified' : 'not verified'}</dd></div>
    </dl>}
  </Card>;
};

const PurchaseHistorySection = () => {
  const transactions = useApi(() => transactionsApi.list(), []);
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

  return <Card
    title="Purchase history by store" icon="store"
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
    <p className="mt-4 text-xs text-gray-500">Synthetic purchase history from demo stores.</p>
  </Card>;
};

const CreditApplicationsSection = () => {
  const applications = useApi(() => applicationsApi.list(), []);

  return <Card
    title="Credit applications" icon="file"
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
  </Card>;
};

/** The dashboard loads only the selected section; its data is reloaded when revisited. */
const AccountDashboard = () => {
  const { user } = useAuth();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [financialRefresh, setFinancialRefresh] = useState(0);
  const requested = sections.find((item) => item.id === params.get('section'))?.id ?? 'overview';
  const socialReturn = ['connected', 'denied', 'linked', 'error'].includes(params.get('social') ?? '');
  const activeSection: DashboardSection = socialReturn ? 'activity' : requested;
  const refreshFinancials = () => setFinancialRefresh((value) => value + 1);

  useEffect(() => {
    window.scrollTo(0, 0);
  }, [activeSection]);

  return (
    <Page
      title={`Hello, ${user?.displayName ?? ''}`}
      subtitle="Your estimates, account details and saved decisions."
      actions={
        <>
          <LinkButton to="/apply" icon="file">Apply for financing</LinkButton>
          <LinkButton to="/stores" variant="secondary" icon="store">Visit stores</LinkButton>
        </>
      }
    >
      <div className="grid items-start gap-6 lg:grid-cols-[14rem_minmax(0,1fr)]">
        <nav aria-label="Dashboard sections" className="hidden rounded-lg border border-primary-700 bg-primary p-3 text-white lg:sticky lg:top-24 lg:block">
          <p className="px-3 pb-3 pt-1 text-xs font-semibold uppercase tracking-wide text-primary-200">Account dashboard</p>
          <ul className="space-y-1">
            {sections.map(({ id, label, icon }) => <li key={id}>
              <Link
                to={sectionUrl(id)}
                aria-current={activeSection === id ? 'page' : undefined}
                className={`flex min-h-11 items-center gap-3 rounded-md px-3 py-2 text-sm font-medium transition-colors focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-primary-200 ${activeSection === id ? 'bg-white/15 text-white' : 'text-primary-100 hover:bg-white/10 hover:text-white'}`}
              ><Icon name={icon} />{label}</Link>
            </li>)}
          </ul>
        </nav>

        <div className="rounded-lg border border-gray-200 bg-white p-4 text-sm font-medium text-gray-700 lg:hidden">
          <label htmlFor="dashboard-section-select">Dashboard section</label>
          <select
            id="dashboard-section-select"
            value={activeSection}
            onChange={(event) => navigate(sectionUrl(event.target.value as DashboardSection))}
            className="mt-2 block w-full rounded-lg min-h-11 border border-gray-400 bg-white px-3 py-2 text-gray-800 focus:border-primary-700 focus:outline-none focus:ring-2 focus:ring-primary-200"
          >
            {sections.map(({ id, label }) => <option key={id} value={id}>{label}</option>)}
          </select>
        </div>

        <div id="dashboard-content" className="min-w-0">
          {activeSection === 'overview' && <AffordabilityDashboardCard refreshKey={financialRefresh} />}
          {activeSection === 'financial' && <FinancialInputsForm onSaved={refreshFinancials} />}
          {activeSection === 'address' && <AddressVerificationPanel refreshKey={financialRefresh} onUpdated={refreshFinancials} />}
          {activeSection === 'activity' && <DemoSignalsPanel onUpdated={refreshFinancials} />}
          {activeSection === 'profile' && <AccountProfileSection />}
          {activeSection === 'purchases' && <PurchaseHistorySection />}
          {activeSection === 'applications' && <CreditApplicationsSection />}
        </div>
      </div>
    </Page>
  );
};

export default AccountDashboard;

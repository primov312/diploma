import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { applicationsApi, meApi, transactionsApi } from '../api/rocket';
import { useAuth } from '../auth/AuthContext';
import { Card, Empty, LinkButton, Loading, Notice, Page, StatusBadge } from '../components/app/Ui';
import { AffordabilityDashboardCard } from '../components/affordability/AffordabilityDashboardCard';
import { IncomeAndHousingCard } from '../components/affordability/IncomeAndHousingCard';
import { AddressVerificationPanel } from '../components/affordability/AddressVerificationPanel';
import { DemoSignalsPanel } from '../components/affordability/DemoSignalsPanel';
import { BudapestDistrictMap, countVisits, districtId } from '../components/map/BudapestDistrictMap';
import { errorMessage, useApi } from '../hooks/useApi';
import { Icon, type IconName } from '../components/common/Icon';
import { formatCurrency } from '../utils/format';

const sections: { id: DashboardSection; icon: IconName; label: string }[] = [
  { id: 'overview', icon: 'chart', label: 'Overview' },
  { id: 'budget', icon: 'wallet', label: 'Budget' },
  { id: 'activity', icon: 'activity', label: 'Activity' },
];
type DashboardSection = 'overview' | 'budget' | 'activity';
/** Older links (?section=financial, address, profile …) still land on the right tab. */
const LEGACY: Record<string, DashboardSection> = { financial: 'budget', address: 'budget', budget: 'budget', activity: 'activity' };

const sectionUrl = (section: DashboardSection) =>
  section === 'overview' ? '/account-dashboard' : `/account-dashboard?section=${section}`;

const RecentApplications = () => {
  const applications = useApi(() => applicationsApi.list(), []);
  return <Card title="Recent applications" icon="file" footer={<Link to="/applications" className="font-medium text-primary hover:text-primary-600">All applications →</Link>}>
    {applications.status === 'loading' && <Loading />}
    {applications.status === 'error' && <Notice tone="error">{errorMessage(applications.error)}</Notice>}
    {applications.status === 'ready' && applications.data.length === 0 && <Empty>No applications yet. <Link to="/marketplace" className="font-medium text-primary underline">Browse the marketplace</Link>.</Empty>}
    {applications.status === 'ready' && applications.data.length > 0 && <ul className="divide-y divide-gray-100">
      {applications.data.slice(0, 3).map((a) => <li key={a.id}>
        <Link to={`/applications/${a.id}`} className="flex flex-wrap items-center justify-between gap-3 py-3 hover:text-primary">
          <span><span className="block font-medium text-gray-800">{a.productName ?? 'Custom amount'} · {formatCurrency(a.requestedAmount)}</span><span className="text-xs text-gray-500">{a.partnerName} · {new Date(a.createdAt).toLocaleDateString()}</span></span>
          <StatusBadge status={a.decisionStatus} />
        </Link>
      </li>)}
    </ul>}
  </Card>;
};

const AtAGlance = () => {
  const profile = useApi(() => meApi.profile(), []);
  const transactions = useApi(() => transactionsApi.list(), []);
  const bySeller = Object.values((transactions.data ?? []).reduce<Record<string, { name: string; count: number }>>((acc, t) => {
    const entry = (acc[t.partnerSlug] ??= { name: t.partnerName, count: 0 });
    if (t.status === 'COMPLETED') entry.count += 1;
    return acc;
  }, {}));
  return <Card title="Your history" icon="user" footer={<Link to="/history" className="font-medium text-primary hover:text-primary-600">All activity →</Link>}>
    {profile.status === 'error' && <Notice tone="error">{errorMessage(profile.error)}</Notice>}
    {profile.status === 'ready' && <ul className="mb-4 flex flex-wrap gap-2 text-xs">
      <li className="rounded-full bg-gray-100 px-3 py-1">Member for {profile.data.accountAgeMonths} months</li>
      <li className={`rounded-full px-3 py-1 ${profile.data.profileComplete ? 'bg-success-50 text-success-700' : 'bg-warning-50 text-warning-700'}`}>Profile {profile.data.profileComplete ? 'complete' : 'incomplete'}</li>
      <li className={`rounded-full px-3 py-1 ${profile.data.emailVerified ? 'bg-success-50 text-success-700' : 'bg-warning-50 text-warning-700'}`}>Email {profile.data.emailVerified ? 'verified' : 'not verified'}</li>
    </ul>}
    {transactions.status === 'loading' && <Loading />}
    {transactions.status === 'ready' && (bySeller.length === 0 ? <p className="text-sm text-gray-600">No purchases yet.</p> :
      <dl className="grid grid-cols-3 gap-2 text-center">{bySeller.map((s) => <div key={s.name} className="rounded-md bg-gray-50 p-3"><dt className="truncate text-xs text-gray-500">{s.name}</dt><dd className="text-xl font-semibold">{s.count}</dd><dd className="text-xs text-gray-500">purchases</dd></div>)}</dl>)}
  </Card>;
};

const PlacesPreview = () => {
  const reports = useApi(() => meApi.demoSignalReports('LOCATION'), []);
  const address = useApi(() => meApi.address(), []);
  const report = reports.status === 'ready' ? reports.data[0] : null;
  const visits = Array.isArray(report?.visits) ? report.visits as Record<string, unknown>[] : [];
  const home = address.status === 'ready' && address.data.available ? districtId(address.data.districtId) : null;
  return <Card title="Where you spend time" icon="pin" footer={<Link to={sectionUrl('activity')} className="font-medium text-primary hover:text-primary-600">{report ? 'Open activity →' : 'Analyze your visits →'}</Link>}>
    {reports.status === 'loading' && <Loading />}
    {reports.status === 'ready' && !report && <p className="text-sm text-gray-600">Run a visit analysis to see your most visited Budapest districts here.</p>}
    {report && <div className="mx-auto max-w-sm"><BudapestDistrictMap counts={countVisits(visits.map((visit) => visit.district))} home={home} /></div>}
  </Card>;
};

/** The dashboard loads only the selected tab; its data is reloaded when revisited. */
const AccountDashboard = () => {
  const { user } = useAuth();
  const [params] = useSearchParams();
  const [financialRefresh, setFinancialRefresh] = useState(0);
  const socialReturn = params.has('social');
  const activeSection: DashboardSection = socialReturn ? 'activity' : LEGACY[params.get('section') ?? ''] ?? 'overview';
  const refreshFinancials = () => setFinancialRefresh((value) => value + 1);

  useEffect(() => { window.scrollTo(0, 0); }, [activeSection]);

  return <Page title={`Hello, ${user?.displayName ?? ''}`}
    actions={<LinkButton to="/apply" icon="file">Apply for credit</LinkButton>}>
    <nav aria-label="Dashboard sections" className="mb-6 flex gap-1 overflow-x-auto border-b border-gray-200">
      {sections.map(({ id, label, icon }) => <Link key={id} to={sectionUrl(id)} aria-current={activeSection === id ? 'page' : undefined}
        className={`-mb-px flex min-h-11 items-center gap-2 border-b-2 px-4 text-sm font-semibold ${activeSection === id ? 'border-primary text-primary' : 'border-transparent text-gray-600 hover:text-gray-900'}`}>
        <Icon name={icon} className="h-4 w-4" />{label}</Link>)}
    </nav>
    <div id="dashboard-content" className="min-w-0">
      {activeSection === 'overview' && <div className="grid gap-6">
        <AffordabilityDashboardCard refreshKey={financialRefresh} />
        <div className="grid gap-6 xl:grid-cols-3">
          <RecentApplications />
          <AtAGlance />
          <PlacesPreview />
        </div>
      </div>}
      {activeSection === 'budget' && <div className="grid gap-6">
        <IncomeAndHousingCard refreshKey={financialRefresh} />
        <AddressVerificationPanel refreshKey={financialRefresh} onUpdated={refreshFinancials} />
      </div>}
      {activeSection === 'activity' && <DemoSignalsPanel onUpdated={refreshFinancials} />}
    </div>
  </Page>;
};

export default AccountDashboard;

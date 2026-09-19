import { Link } from 'react-router-dom';
import { applicationsApi } from '../../api/rocket';
import { Card, Empty, LinkButton, Loading, Notice, Page, StatusBadge } from '../../components/app/Ui';
import { errorMessage, useApi } from '../../hooks/useApi';
import { formatCurrency } from '../../utils/format';

const ApplicationsPage = () => {
  const list = useApi(() => applicationsApi.list(), []);
  return (
    <Page title="Credit applications" subtitle="Every request you submitted, with its saved decision." actions={<LinkButton to="/apply">New request</LinkButton>}>
      <Card>
        {list.status === 'loading' && <Loading />}
        {list.status === 'error' && <Notice tone="error">{errorMessage(list.error)}</Notice>}
        {list.status === 'ready' && list.data.length === 0 && <Empty>No applications yet.</Empty>}
        {list.status === 'ready' && list.data.length > 0 && (
          <ul className="divide-y divide-gray-100">
            {list.data.map((a) => (
              <li key={a.id} className="flex flex-wrap items-center justify-between gap-3 py-3">
                <div>
                  <Link to={`/applications/${a.id}`} className="font-medium text-gray-800 hover:text-primary">
                    {formatCurrency(a.requestedAmount)} at {a.partnerName}
                    {a.productName ? ` · ${a.productName}` : ''}
                  </Link>
                  <div className="text-xs text-gray-500">
                    {new Date(a.createdAt).toLocaleString()} · possible {formatCurrency(a.possibleAmount)} · {a.aiStatus === 'APPLIED' ? 'rules + AI' : 'rules'}
                  </div>
                </div>
                <StatusBadge status={a.decisionStatus} />
              </li>
            ))}
          </ul>
        )}
      </Card>
    </Page>
  );
};

export default ApplicationsPage;

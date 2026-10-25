import { Link } from 'react-router-dom';
import { applicationsApi } from '../../api/rocket';
import { Card, Empty, LinkButton, Loading, Notice, Page, StatusBadge } from '../../components/app/Ui';
import { Icon } from '../../components/common/Icon';
import { errorMessage, useApi } from '../../hooks/useApi';
import { formatCurrency } from '../../utils/format';

const ApplicationsPage = () => {
  const list = useApi(() => applicationsApi.list(), []);
  return (
    <Page title="Applications" actions={<LinkButton to="/apply">New application</LinkButton>}>
      <Card>
        {list.status === 'loading' && <Loading />}
        {list.status === 'error' && <Notice tone="error">{errorMessage(list.error)}</Notice>}
        {list.status === 'ready' && list.data.length === 0 && <Empty>No applications yet. <Link to="/marketplace" className="font-medium text-primary underline">Find something in the marketplace</Link>.</Empty>}
        {list.status === 'ready' && list.data.length > 0 && (
          <ul className="divide-y divide-gray-100">
            {list.data.map((a) => (
              <li key={a.id}>
                <Link to={`/applications/${a.id}`} className="-mx-2 flex flex-wrap items-center gap-x-4 gap-y-2 rounded-md px-2 py-3 hover:bg-gray-50">
                  <div className="min-w-0 flex-1">
                    <div className="font-medium text-gray-800">{a.productName ?? 'Custom amount'} · {formatCurrency(a.requestedAmount)}</div>
                    <div className="text-sm text-gray-500">{a.partnerName} · {new Date(a.createdAt).toLocaleDateString()}{a.loanId ? ' · accepted' : ''}</div>
                  </div>
                  <StatusBadge status={a.decisionStatus} />
                  <Icon name="arrow" className="h-4 w-4 text-gray-400" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </Card>
    </Page>
  );
};

export default ApplicationsPage;

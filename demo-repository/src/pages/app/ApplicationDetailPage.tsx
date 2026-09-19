import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { applicationsApi } from '../../api/rocket';
import { Card, LinkButton, Loading, Notice, Page, StatusBadge } from '../../components/app/Ui';
import { errorMessage, useApi } from '../../hooks/useApi';
import { formatCurrency } from '../../utils/format';
import { AI_STATUS_TEXT, FACTOR_LABEL, STATUS_TEXT, reasonText } from '../../utils/reasons';

const ApplicationDetailPage = () => {
  const { id } = useParams();
  const app = useApi(() => applicationsApi.get(id!), [id]);

  if (app.status === 'loading') return <Page title="Decision"><Loading /></Page>;
  if (app.status === 'error') {
    const notFound = app.error instanceof ApiError && app.error.status === 404;
    return (
      <Page title="Decision">
        <Notice tone="error">{notFound ? 'This application does not exist or is not yours.' : errorMessage(app.error)}</Notice>
        <div className="mt-4"><LinkButton to="/applications" variant="secondary">Back to applications</LinkButton></div>
      </Page>
    );
  }

  const a = app.data;
  const status = STATUS_TEXT[a.decisionStatus];
  const lowerSuggestion = a.decisionStatus === 'REJECTED' && a.possibleAmount > 0 && a.possibleAmount < a.requestedAmount;

  return (
    <Page
      title={`${formatCurrency(a.requestedAmount)} at ${a.partnerName}`}
      subtitle={`Submitted ${new Date(a.createdAt).toLocaleString()}${a.productName ? ` · ${a.productName}` : ''}`}
      actions={<LinkButton to="/applications" variant="secondary">All applications</LinkButton>}
    >
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <div className="flex flex-wrap items-center gap-3">
            <StatusBadge status={a.decisionStatus} />
            <span className="text-sm text-gray-600">score {a.score.toFixed(2)} · policy {a.policyVersion}{a.modelVersion ? ` · model ${a.modelVersion}` : ''}</span>
          </div>
          <p className="mt-4 text-gray-700">{status.summary}</p>

          <dl className="mt-6 grid gap-4 sm:grid-cols-2">
            <div className="rounded-xl border border-gray-100 p-4">
              <dt className="text-sm text-gray-600">Requested</dt>
              <dd className="text-2xl font-bold text-gray-800">{formatCurrency(a.requestedAmount)}</dd>
            </div>
            <div className="rounded-xl border border-gray-100 p-4">
              <dt className="text-sm text-gray-600">Possible amount at this store</dt>
              <dd className="text-2xl font-bold text-gray-800">{formatCurrency(a.possibleAmount)}</dd>
            </div>
          </dl>

          {lowerSuggestion && (
            <div className="mt-4">
              <Notice tone="info">
                A lower amount looks possible. This is a suggestion, not an offer:{' '}
                <Link to={`/apply?partner=${a.partnerSlug}&amount=${a.possibleAmount}`} className="font-medium text-primary">
                  submit a new request for {formatCurrency(a.possibleAmount)}
                </Link>
                .
              </Notice>
            </div>
          )}

          <h2 className="mt-8 text-lg font-semibold text-gray-800">Why</h2>
          {a.reasons.length === 0 ? (
            <p className="mt-2 text-sm text-gray-600">No negative factors were found.</p>
          ) : (
            <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-gray-700">
              {a.reasons.map((code) => (
                <li key={code}>{reasonText(code)} <span className="text-xs text-gray-400">({code})</span></li>
              ))}
            </ul>
          )}
          <p className="mt-4 text-xs text-gray-500">{AI_STATUS_TEXT[a.aiStatus]}</p>
        </Card>

        <Card title="Factors">
          <ul className="space-y-4 text-sm">
            {Object.entries(a.factors).map(([key, f]) => (
              <li key={key}>
                <div className="flex items-center justify-between">
                  <span className="font-medium text-gray-800">{FACTOR_LABEL[key] ?? key}</span>
                  <span className="text-gray-600">{f.score === null ? (key === 'affordability' ? 'check' : 'no evidence') : `${(f.score * 100).toFixed(0)} / 100`}{f.weight > 0 ? ` · weight ${Math.round(f.weight * 100)}%` : ''}</span>
                </div>
                {f.score !== null && (
                  <div className="mt-1 h-2 rounded-full bg-gray-100" aria-hidden="true">
                    <div className="h-2 rounded-full bg-gradient-primary" style={{ width: `${Math.round(f.score * 100)}%` }} />
                  </div>
                )}
                {f.reasons.length > 0 && <div className="mt-1 text-xs text-gray-500">{f.reasons.map(reasonText).join(' ')}</div>}
              </li>
            ))}
          </ul>
          <p className="mt-6 text-xs text-gray-500">Features prepared {a.preparationMode.toLowerCase()} at {new Date(a.observedAt).toLocaleTimeString()}. No money is moved by any outcome.</p>
        </Card>
      </div>
    </Page>
  );
};

export default ApplicationDetailPage;

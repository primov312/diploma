import CreditAcceptance from '../../components/payments/CreditAcceptance';
import { Link, useParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { applicationsApi } from '../../api/rocket';
import { Card, LinkButton, Loading, Notice, Page, StatusBadge } from '../../components/app/Ui';
import { Icon } from '../../components/common/Icon';
import { errorMessage, useApi } from '../../hooks/useApi';
import { cn } from '../../utils/cn';
import { formatCurrency, usdToHuf } from '../../utils/format';
import { AI_STATUS_TEXT, FACTOR_LABEL, STATUS_TEXT, isBudgetNote, reasonText } from '../../utils/reasons';

const HERO: Record<string, string> = {
  APPROVED: 'border-success-100 bg-success-50',
  REJECTED: 'border-error-100 bg-error-50',
  REVIEW: 'border-warning-100 bg-warning-50',
};

const ApplicationDetailPage = () => {
  const { id } = useParams();
  const app = useApi(() => applicationsApi.get(id!), [id]);

  if (app.status === 'loading') return <Page title="Decision"><Loading /></Page>;
  if (app.status === 'error') {
    const notFound = app.error instanceof ApiError && app.error.status === 404;
    return <Page title="Decision">
      <Notice tone="error">{notFound ? 'This application does not exist or is not yours.' : errorMessage(app.error)}</Notice>
      <div className="mt-4"><LinkButton to="/applications" variant="secondary">Back to applications</LinkButton></div>
    </Page>;
  }

  const a = app.data;
  const status = STATUS_TEXT[a.decisionStatus];
  const reasons = a.reasons.filter((code) => !isBudgetNote(code));
  const notes = a.reasons.filter(isBudgetNote);
  const lowerSuggestion = a.decisionStatus === 'REJECTED' && a.possibleAmount > 0 && a.possibleAmount < a.requestedAmount;

  return <Page title={a.productName ?? 'Credit application'}
    subtitle={`${a.partnerName} · ${new Date(a.createdAt).toLocaleString()}`}
    actions={<LinkButton to="/applications" variant="secondary">All applications</LinkButton>}>
    <section className={cn('mb-6 rounded-lg border p-5 md:p-6', HERO[a.decisionStatus])} aria-label="Decision">
      <div className="flex flex-wrap items-center gap-3"><StatusBadge status={a.decisionStatus} /><span className="text-sm text-gray-600">{a.aiStatus === 'APPLIED' ? 'Rules + AI' : 'Rules'}</span></div>
      <p className="mt-3 text-3xl font-bold text-gray-900">{formatCurrency(a.requestedAmount)}</p>
      <p className="mt-2 max-w-2xl text-gray-700">{a.loanId ? 'Accepted. The seller was paid and your repayments are scheduled.' : status.summary}</p>
      {lowerSuggestion && <Link to={`/apply?partner=${a.partnerSlug}&amount=${usdToHuf(a.possibleAmount)}`} className="btn-primary mt-4">
        Try {formatCurrency(a.possibleAmount)} instead<Icon name="arrow" /></Link>}
    </section>

    <CreditAcceptance application={a} />

    <div className="grid gap-6 lg:grid-cols-3">
      <Card title="Why" className="lg:col-span-2">
        {reasons.length === 0
          ? <p className="flex items-center gap-2 text-gray-700"><Icon name="check" className="h-5 w-5 text-success-700" />Nothing held this request back.</p>
          : <ul className="space-y-2">{reasons.map((code) => <li key={code} className="flex gap-2 text-gray-700"><Icon name="info" className="mt-0.5 h-5 w-5 text-gray-500" />{reasonText(code)}</li>)}</ul>}
        {notes.length > 0 && <p className="mt-4 text-sm text-gray-500">Budget: {notes.map(reasonText).join(' ')}</p>}
        <p className="mt-4 text-sm text-gray-600">Possible amount with {a.partnerName}: <strong>{formatCurrency(a.possibleAmount)}</strong></p>
        <details className="mt-5"><summary>Technical details</summary>
          <p>Score {a.score.toFixed(2)} · policy {a.policyVersion}{a.modelVersion ? ` · model ${a.modelVersion}` : ''} · {AI_STATUS_TEXT[a.aiStatus]}</p>
          <p className="mt-2">Features prepared {a.preparationMode.toLowerCase()} at {new Date(a.observedAt).toLocaleString()}. Codes: {a.reasons.join(', ') || 'none'}.</p>
        </details>
      </Card>
      <Card title="Score breakdown" icon="chart">
        <ul className="space-y-4 text-sm">{Object.entries(a.factors).map(([key, f]) => <li key={key}>
          <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="font-medium text-gray-800">{FACTOR_LABEL[key] ?? key}</span>
            <span className="tabular-nums text-gray-600">{f.score === null ? (key === 'affordability' ? 'check' : 'no data') : `${Math.round(f.score * 100)} / 100`}</span>
          </div>
          {f.score !== null && <div className="mt-1 h-2 rounded-full bg-gray-100" aria-hidden="true"><div className="h-2 rounded-full bg-accent" style={{ width: `${Math.round(f.score * 100)}%` }} /></div>}
          {f.weight > 0 && <div className="mt-1 text-xs text-gray-500">weight {Math.round(f.weight * 100)}%</div>}
        </li>)}</ul>
      </Card>
    </div>
  </Page>;
};

export default ApplicationDetailPage;

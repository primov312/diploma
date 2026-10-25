import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { meApi } from '../../api/rocket';
import type { DemoSignalReport } from '../../api/types';
import { errorMessage, useApi } from '../../hooks/useApi';
import { Card, Notice, Loading } from '../app/Ui';
import { Icon } from '../common/Icon';
import LocalDropdown from '../common/LocalDropdown';
import { BudapestDistrictMap, countVisits, districtId } from '../map/BudapestDistrictMap';
import { LocationPricingSummary, LocationVisitBenchmark } from './LocationBenchmarkPrices';

const PROVIDER_NAMES: Record<string, string> = { TYPESAFE: 'Jev', ANTHROPIC: 'Claude', GEMINI: 'Gemini' };

const LOCATION_SCENARIOS = [
  { value: 'budapest-priced-month', label: 'A month in Budapest' },
  { value: 'budapest-priced-week', label: 'A week in Budapest' },
  { value: 'regular-week', label: 'Regular week' },
  { value: 'sparse', label: 'Very few visits' },
  { value: 'contradictory', label: 'Mixed signals' },
];
const SOCIAL_SCENARIOS = [
  { value: 'ordinary', label: 'Ordinary posting' },
  { value: 'repetitive', label: 'Repetitive posting' },
  { value: 'inconsistent', label: 'Inconsistent dates' },
  { value: 'sparse', label: 'Very few posts' },
  { value: 'injection', label: 'Prompt-injection test' },
];
const LIVE_SCENARIO = 'facebook-live';
const COMPARISON: Record<string, { text: string; tone: string }> = {
  MATCHES_DECLARED_DISTRICT: { text: 'Matches your address', tone: 'border-success-100 bg-success-50 text-success-700' },
  DOES_NOT_MATCH_DECLARED_DISTRICT: { text: 'Differs from your address', tone: 'border-warning-100 bg-warning-50 text-warning-700' },
  NO_DECLARED_DISTRICT: { text: 'No address saved', tone: 'border-gray-200 bg-gray-50 text-gray-700' },
  INSUFFICIENT_HISTORY: { text: 'Not enough visits yet', tone: 'border-gray-200 bg-gray-50 text-gray-700' },
};
const CONNECT_OUTCOMES: Record<string, { tone: 'info' | 'error' | 'warning'; text: string }> = {
  connected: { tone: 'info', text: 'Facebook connected. You can analyze your posts now.' },
  denied: { tone: 'warning', text: 'Facebook login was cancelled. Nothing was connected.' },
  permission: { tone: 'warning', text: 'Facebook was not connected because access to posts was not allowed. Try again and keep “posts” selected.' },
  session: { tone: 'warning', text: 'Your session ended during Facebook login. Please connect again.' },
  linked: { tone: 'error', text: 'That Facebook account is already linked to another user.' },
  error: { tone: 'error', text: 'Facebook connection failed. Please try again.' },
};
const KEY_METRICS: [string, string][] = [['postCount', 'Posts'], ['reactionCount', 'Reactions'], ['repeatedTextCount', 'Repeated posts'], ['distinctLocations', 'Places']];

const asError = (cause: unknown) => errorMessage(cause instanceof Error ? cause : new Error(String(cause)));
const asList = (value: unknown) => (Array.isArray(value) ? value : []) as Record<string, unknown>[];

function LocationCard({ onUpdated }: { onUpdated: () => void }) {
  const reports = useApi(() => meApi.demoSignalReports('LOCATION'), []);
  const address = useApi(() => meApi.address(), []);
  const [scenario, setScenario] = useState(LOCATION_SCENARIOS[0].value);
  const [fresh, setFresh] = useState<DemoSignalReport | null>(null);
  const [selected, setSelected] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  const run = async () => {
    setBusy(true); setError('');
    try { setFresh((await meApi.runLocation(scenario)).report); setSelected(null); reports.reload(); onUpdated(); }
    catch (cause) { setError(asError(cause)); }
    finally { setBusy(false); }
  };

  const report = fresh ?? (reports.status === 'ready' ? reports.data[0] ?? null : null);
  const visits = asList(report?.visits);
  const counts = countVisits(visits.map((visit) => visit.district));
  const ranking = Object.entries(counts).sort((a, b) => b[1] - a[1]);
  const home = address.status === 'ready' && address.data.available ? districtId(address.data.districtId) : null;
  const shown = selected ? visits.filter((visit) => districtId(visit.district) === selected) : visits;
  const comparison = COMPARISON[String(report?.declaredDistrictComparison ?? '')];

  return <Card title="Where you spend time" icon="pin">
    <div className="flex flex-wrap items-center gap-2">
      <LocalDropdown value={scenario} onValueChange={setScenario} aria-label="Visit history sample" className="w-full min-w-0 sm:w-60" options={LOCATION_SCENARIOS} />
      <button onClick={run} disabled={busy} className="btn-primary px-4 py-2 text-sm"><Icon name="pin" />{busy ? 'Analyzing…' : 'Analyze visits'}</button>
    </div>
    {error && <div className="mt-4"><Notice tone="error">{error}</Notice></div>}
    {reports.status === 'loading' && <Loading label="Loading your last analysis…" />}
    {reports.status === 'error' && <div className="mt-4"><Notice tone="error">{errorMessage(reports.error)}</Notice></div>}
    {reports.status === 'ready' && !report && <p className="mt-6 text-sm text-gray-600">Pick a sample and press <strong>Analyze visits</strong> to see your most visited districts.</p>}
    {report && <div className="mt-6 grid gap-6 lg:grid-cols-[minmax(0,34rem)_minmax(16rem,1fr)] lg:gap-10">
      <BudapestDistrictMap counts={counts} home={home} selected={selected} onSelect={setSelected} />
      <div className="space-y-5">
        <dl className="grid grid-cols-3 gap-2 text-center">
          {[['Visits', report.visitCount], ['Districts', report.distinctDistricts], ['Top', ranking[0] ? `${ranking[0][0]}.` : '—']].map(([label, value]) =>
            <div key={String(label)} className="rounded-md bg-gray-50 p-3"><dt className="text-xs text-gray-500">{String(label)}</dt><dd className="text-xl font-semibold">{String(value ?? '—')}</dd></div>)}
        </dl>
        {comparison && <p className={`inline-flex items-center gap-2 rounded-md border px-3 py-1.5 text-sm font-medium ${comparison.tone}`}><Icon name="home" className="h-4 w-4" />{comparison.text}</p>}
        <div>
          <h3 className="mb-2 text-sm font-semibold text-gray-800">Most visited districts</h3>
          <ol className="space-y-1">{ranking.map(([id, count]) => <li key={id}>
            <button type="button" onClick={() => setSelected(selected === id ? null : id)} aria-pressed={selected === id}
              className={`flex min-h-11 w-full items-center gap-3 rounded-md px-2 text-left text-sm hover:bg-gray-50 ${selected === id ? 'bg-primary-50 font-semibold' : ''}`}>
              <span className="w-12 shrink-0 font-medium">{id}.</span>
              <span className="h-2 flex-1 rounded-full bg-gray-100" aria-hidden="true"><span className="block h-2 rounded-full bg-accent" style={{ width: `${(count / ranking[0][1]) * 100}%` }} /></span>
              <span className="w-16 shrink-0 text-right tabular-nums text-gray-600">{count} visit{count === 1 ? '' : 's'}</span>
            </button>
          </li>)}</ol>
        </div>
      </div>
    </div>}
    {report && <details className="mt-6" open={selected != null || undefined}>
      <summary>{selected ? `Visits in district ${selected} (${shown.length})` : `All visits (${visits.length})`}</summary>
      {selected && <button type="button" className="mb-3 text-sm underline" onClick={() => setSelected(null)}>Show all districts</button>}
      <ol className="max-h-96 space-y-3 overflow-auto pr-1">{shown.map((visit) => <li key={String(visit.id)} className="border-b border-gray-100 pb-3">
        <div className="flex flex-wrap justify-between gap-x-3 gap-y-1"><span className="font-medium text-gray-800">{String(visit.place)} · {String(visit.district)}</span><time className="text-xs text-gray-500">{new Date(String(visit.arrival)).toLocaleString()}</time></div>
        <LocationVisitBenchmark value={visit.pricing} status={visit.pricingStatus} />
      </li>)}</ol>
      <div className="mt-4"><LocationPricingSummary value={report.pricing} /></div>
      <p className="mt-3 text-xs text-gray-500">{String(report.limitation ?? '')}</p>
    </details>}
  </Card>;
}

function SocialCard() {
  const reports = useApi(() => meApi.demoSignalReports('SOCIAL'), []);
  const connection = useApi(() => meApi.socialConnection(), []);
  const [params, setParams] = useSearchParams();
  const outcome = CONNECT_OUTCOMES[params.get('social') ?? ''];
  const [choice, setChoice] = useState('');
  const [fresh, setFresh] = useState<DemoSignalReport | null>(null);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');
  const connected = connection.status === 'ready' && connection.data.connected;
  const scenario = choice || (connected ? LIVE_SCENARIO : SOCIAL_SCENARIOS[0].value);

  const act = async (kind: string, work: () => Promise<void>) => {
    setBusy(kind); setError('');
    try { await work(); } catch (cause) { setError(asError(cause)); } finally { setBusy(''); }
  };
  const connect = () => act('CONNECT', async () => { window.location.assign((await meApi.startFacebookConnect()).authorizeUrl); });
  const disconnect = () => act('CONNECT', async () => { await meApi.disconnectSocial(); connection.reload(); setChoice(''); setFresh(null); });
  const run = () => act('RUN', async () => { setFresh((await meApi.runSocial(scenario)).report); reports.reload(); });
  const dismiss = () => setParams((current) => { const next = new URLSearchParams(current); next.delete('social'); next.set('section', 'activity'); return next; }, { replace: true });

  const report = fresh ?? (reports.status === 'ready' ? reports.data[0] ?? null : null);
  const findings = asList(report?.findings);
  const evidence = new Map(asList(report?.evidence).map((item) => [String(item.id), item]));
  const metrics = (report?.metrics && typeof report.metrics === 'object' ? report.metrics : {}) as Record<string, unknown>;
  const ai = report?.analysisMode === 'AI' && report.status === 'COMPLETE';

  return <Card title="Social activity" icon="activity">
    {outcome && <div className="mb-4"><Notice tone={outcome.tone}>{outcome.text} <button type="button" className="underline" onClick={dismiss}>Dismiss</button></Notice></div>}
    {connection.status === 'ready' && <div className="mb-4 flex flex-wrap items-center justify-between gap-3 rounded-md border border-gray-200 p-3 text-sm">
      {!connection.data.configured && <span className="text-gray-600">Facebook login is not set up on this server. Samples are available.</span>}
      {connection.data.configured && !connected && <><span className="text-gray-700">Analyze your own recent Facebook posts.</span>
        <button onClick={connect} disabled={busy !== ''} className="btn-secondary px-4 py-2 text-sm">{busy === 'CONNECT' ? 'Opening Facebook…' : 'Connect Facebook'}</button></>}
      {connected && <><span className="text-gray-700">Connected as <strong>{connection.data.displayName ?? 'Facebook user'}</strong></span>
        <button onClick={disconnect} disabled={busy !== ''} className="text-sm underline">Disconnect</button></>}
    </div>}
    {connection.status === 'error' && <div className="mb-4"><Notice tone="error">{errorMessage(connection.error)}</Notice></div>}
    <div className="flex flex-wrap items-center gap-2">
      <LocalDropdown value={scenario} onValueChange={setChoice} aria-label="Posts to analyze" className="w-full min-w-0 sm:w-60"
        options={[...(connected ? [{ value: LIVE_SCENARIO, label: 'My Facebook posts' }] : []), ...SOCIAL_SCENARIOS.map((item) => ({ ...item, label: `${item.label} (sample)` }))]} />
      <button onClick={run} disabled={busy !== ''} className="btn-primary px-4 py-2 text-sm"><Icon name="activity" />{busy === 'RUN' ? 'Analyzing…' : 'Analyze posts'}</button>
    </div>
    {error && <div className="mt-4"><Notice tone="error">{error}</Notice></div>}
    {reports.status === 'loading' && <Loading label="Loading your last analysis…" />}
    {reports.status === 'error' && <div className="mt-4"><Notice tone="error">{errorMessage(reports.error)}</Notice></div>}
    {report && <div className="mt-5 space-y-4">
      <div className="flex flex-wrap items-center gap-2 text-xs">
        <span className="rounded border border-gray-200 bg-gray-50 px-2 py-0.5 font-medium text-gray-700">{report.dataSource === 'OWNER_ACCOUNT' ? 'Your Facebook posts' : 'Sample posts'}</span>
        <span className="rounded border border-gray-200 bg-gray-50 px-2 py-0.5 font-medium text-gray-700">{ai ? `${PROVIDER_NAMES[String(report.provider)] ?? 'AI'} · ${String(report.modelVersion)}` : 'Rule-based'}</span>
      </div>
      {report.status === 'AI_UNAVAILABLE' && <Notice tone="warning">AI analysis is unavailable right now, so only the counts are shown.</Notice>}
      <p className="text-gray-800">{String(report.summary ?? '')}</p>
      <dl className="grid grid-cols-2 gap-2 text-center sm:grid-cols-4">{KEY_METRICS.filter(([key]) => key in metrics).map(([key, label]) =>
        <div key={key} className="rounded-md bg-gray-50 p-3"><dt className="text-xs text-gray-500">{label}</dt><dd className="text-lg font-semibold">{String(metrics[key])}</dd></div>)}</dl>
      {findings.length > 0 && <ul className="space-y-2">{findings.map((finding, index) => <li key={index} className="rounded-md border border-gray-100 p-3 text-sm">
        <div className="flex flex-wrap items-center justify-between gap-2"><strong>{String(finding.label).replaceAll('_', ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase())}</strong>
          <span className="text-xs text-gray-500">{String(finding.confidence).toLowerCase()} confidence</span></div>
        <p className="mt-1 text-gray-600">{String(finding.observation)}</p>
        {Array.isArray(finding.evidenceIds) && finding.evidenceIds.length > 0 && <details className="mt-2"><summary>Evidence ({finding.evidenceIds.length})</summary>
          <ul className="space-y-1 text-xs">{(finding.evidenceIds as string[]).slice(0, 5).map((id) => <li key={id}><code>{id}</code> {String(evidence.get(id)?.excerpt ?? '')}</li>)}</ul>
        </details>}
      </li>)}</ul>}
      {Array.isArray(report.limitations) && <p className="text-xs text-gray-500">{(report.limitations as string[]).join(' ')}</p>}
    </div>}
  </Card>;
}

/** Location and social demo analyses. Neither changes a credit decision directly; location visits feed the spending forecast. */
export function DemoSignalsPanel({ onUpdated = () => {} }: { onUpdated?: () => void }) {
  return <div className="grid gap-6">
    <LocationCard onUpdated={onUpdated} />
    <SocialCard />
  </div>;
}

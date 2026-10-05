import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { meApi } from '../../api/rocket';
import type { DemoSignalReport } from '../../api/types';
import { errorMessage, useApi } from '../../hooks/useApi';
import { Card, Notice, Loading, SyntheticTag } from '../app/Ui';
import { Icon } from '../common/Icon';
import LocalDropdown from '../common/LocalDropdown';
import { LocationPricingSummary, LocationVisitBenchmark } from './LocationBenchmarkPrices';

const locations = ['budapest-priced-month', 'budapest-priced-week', 'regular-week', 'sparse', 'contradictory'];
const socialScenarios = ['ordinary', 'repetitive', 'inconsistent', 'sparse', 'injection'];
const LIVE_SCENARIO = 'facebook-live';
const connectOutcomes: Record<string, { tone: 'info' | 'error' | 'warning'; text: string }> = {
  connected: { tone: 'info', text: 'Facebook account connected. You can now analyze it.' },
  denied: { tone: 'warning', text: 'Facebook login was cancelled. Nothing was connected.' },
  linked: { tone: 'error', text: 'That Facebook account is already linked to another user.' },
  error: { tone: 'error', text: 'Facebook connection failed. Please try again.' },
};

export function DemoSignalsPanel({ onUpdated = () => {} }: { onUpdated?: () => void }) {
  const locationReports = useApi(() => meApi.demoSignalReports('LOCATION'), []);
  const socialReports = useApi(() => meApi.demoSignalReports('SOCIAL'), []);
  const [locationScenario, setLocationScenario] = useState(locations[0]);
  const connection = useApi(() => meApi.socialConnection(), []);
  const [params, setParams] = useSearchParams();
  const outcome = connectOutcomes[params.get('social') ?? ''];
  const [socialChoice, setSocialChoice] = useState('');
  const connected = connection.status === 'ready' && connection.data.connected;
  const socialScenario = socialChoice || (connected ? LIVE_SCENARIO : socialScenarios[0]);
  const setSocialScenario = setSocialChoice;
  const [locationResult, setLocationResult] = useState<DemoSignalReport | null>(null);
  const [socialResult, setSocialResult] = useState<DemoSignalReport | null>(null);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');

  const connectFacebook = async () => {
    setBusy('CONNECT'); setError('');
    try { window.location.assign((await meApi.startFacebookConnect()).authorizeUrl); }
    catch (cause) { setError(errorMessage(cause instanceof Error ? cause : new Error(String(cause)))); setBusy(''); }
  };

  const disconnectFacebook = async () => {
    setBusy('CONNECT'); setError('');
    try { await meApi.disconnectSocial(); connection.reload(); setSocialChoice(''); setSocialResult(null); }
    catch (cause) { setError(errorMessage(cause instanceof Error ? cause : new Error(String(cause)))); }
    finally { setBusy(''); }
  };

  const run = async (kind: 'LOCATION' | 'SOCIAL') => {
    setBusy(kind); setError('');
    try {
      const result = kind === 'LOCATION' ? await meApi.runLocation(locationScenario) : await meApi.runSocial(socialScenario);
      if (kind === 'LOCATION') { setLocationResult(result.report); locationReports.reload(); onUpdated(); }
      else { setSocialResult(result.report); socialReports.reload(); }
    } catch (cause) { setError(errorMessage(cause instanceof Error ? cause : new Error(String(cause)))); }
    finally { setBusy(''); }
  };

  const location = locationResult ?? (locationReports.status === 'ready' ? locationReports.data[0] : null);
  const social = socialResult ?? (socialReports.status === 'ready' ? socialReports.data[0] : null);
  const visits = Array.isArray(location?.visits) ? location.visits as Record<string, unknown>[] : [];
  const findings = Array.isArray(social?.findings) ? social.findings as Record<string, unknown>[] : [];
  const evidence = new Map((Array.isArray(social?.evidence) ? social.evidence as Record<string, unknown>[] : []).map((item) => [String(item.id), item]));
  const metrics = social?.metrics && typeof social.metrics === 'object' ? social.metrics as Record<string, unknown> : {};

  return <Card title="Activity analysis" icon="activity" className="lg:col-span-3">
    {outcome && <div className="mb-4"><Notice tone={outcome.tone}>{outcome.text} <button type="button" className="underline" onClick={() => setParams(current => {
      const next = new URLSearchParams(current);
      next.delete('social');
      next.set('section', 'activity');
      return next;
    }, { replace: true })}>Dismiss</button></Notice></div>}
    <div className="grid gap-6 xl:grid-cols-2">
      <section className="rounded-md border border-gray-200 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div><h3 className="flex items-center gap-2 font-semibold text-gray-800"><Icon name="pin" />Location history</h3><p className="text-xs text-gray-500">Synthetic visits · district price estimates</p></div>
        </div>
        <div className="mt-4 flex flex-wrap gap-2">
          <LocalDropdown value={locationScenario} onValueChange={setLocationScenario} aria-label="Location scenario" className="w-full min-w-0 sm:w-56" options={locations.map((id) => ({ value: id, label: id.replace('-', ' ') }))} />
          <button onClick={() => run('LOCATION')} disabled={busy !== ''} className="btn-secondary px-4 py-2 text-sm"><Icon name="pin" />{busy === 'LOCATION' ? 'Analyzing…' : 'Run location analysis'}</button>
        </div>
        {locationReports.status === 'loading' && <Loading label="Loading location reports…" />}
        {locationReports.status === 'error' && <Notice tone="error">{errorMessage(locationReports.error)}</Notice>}
        {location && <div className="mt-4 space-y-3">
          <div className="grid grid-cols-2 gap-2 sm:grid-cols-3 text-center text-sm">
            {[['Visits', location.visitCount], ['Districts', location.distinctDistricts], ['Most visited', location.mostVisitedDistrict]].map(([label, value]) => <div key={String(label)} className="rounded-lg bg-gray-50 p-3"><div className="text-xs text-gray-500">{String(label)}</div><strong>{String(value ?? '—')}</strong></div>)}
          </div>
          <div className="rounded-lg bg-primary-50 p-3" aria-label="Schematic map of synthetic visit districts">
            <div className="text-xs font-semibold uppercase tracking-wide text-primary-900">Observed visits · schematic</div>
            <div className="mt-2 flex flex-wrap gap-2">{Array.from(new Set(visits.map((visit) => String(visit.district ?? '')))).filter(Boolean).map((district) => <span key={district} className="rounded-full bg-white px-3 py-1 text-xs text-primary-900">{district}</span>)}</div>
          </div>
          <LocationPricingSummary value={location.pricing} />
          <ol className="max-h-80 space-y-3 overflow-auto text-sm">{visits.map((visit) => <li key={String(visit.id)} className="border-b border-gray-100 pb-3">
            <div className="flex flex-wrap justify-between gap-x-3 gap-y-1"><span className="font-medium">{String(visit.place)} · {String(visit.district)}</span><time className="text-xs text-gray-500">{new Date(String(visit.arrival)).toLocaleString()}</time></div>
            <LocationVisitBenchmark value={visit.pricing} status={visit.pricingStatus} />
          </li>)}</ol>
          <details><summary>Location analysis limitations</summary><p>{String(location.limitation ?? '')}</p></details>
        </div>}
      </section>

      <section className="rounded-md border border-gray-200 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div><h3 className="flex items-center gap-2 font-semibold text-gray-800"><Icon name="activity" />Social activity</h3><p className="text-xs text-gray-500">Connected Facebook posts or synthetic scenarios</p></div>
        </div>
        {connection.status === 'ready' && <div className="mt-4 rounded-lg bg-primary-50 p-3 text-sm">
          {!connection.data.configured && <p className="text-primary-900">Facebook login is not configured on this server, so only synthetic scenarios are available.</p>}
          {connection.data.configured && !connection.data.connected && <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-primary-900">Sign in with Facebook to analyze your own recent posts. Your password is never shared with this app.</span>
            <button onClick={connectFacebook} disabled={busy !== ''} className="btn-secondary px-4 py-2 text-sm">{busy === 'CONNECT' ? 'Opening Facebook…' : 'Connect Facebook'}</button>
          </div>}
          {connection.data.connected && <div className="flex flex-wrap items-center justify-between gap-2">
            <span className="text-primary-900">Connected as <strong>{connection.data.displayName ?? 'Facebook user'}</strong></span>
            <button onClick={disconnectFacebook} disabled={busy !== ''} className="text-sm text-primary-900 underline">Disconnect</button>
          </div>}
        </div>}
        <div className="mt-4 flex flex-wrap gap-2">
          <LocalDropdown value={socialScenario} onValueChange={setSocialScenario} aria-label="Social activity source" className="w-full min-w-0 sm:w-56" options={[...(connected ? [{ value: LIVE_SCENARIO, label: 'My Facebook account (live)' }] : []), ...socialScenarios.map((id) => ({ value: id, label: `${id} (synthetic)` }))]} />
          <button onClick={() => run('SOCIAL')} disabled={busy !== ''} className="btn-secondary px-4 py-2 text-sm"><Icon name="activity" />{busy === 'SOCIAL' ? 'Analyzing…' : 'Run social analysis'}</button>
        </div>
        {connection.status === 'error' && <Notice tone="error">{errorMessage(connection.error)}</Notice>}
        {socialReports.status === 'loading' && <Loading label="Loading social reports…" />}
        {socialReports.status === 'error' && <Notice tone="error">{errorMessage(socialReports.error)}</Notice>}
        {social && <div className="mt-4 space-y-3">
          <SyntheticTag>{social.dataSource === 'OWNER_ACCOUNT' ? 'Your Facebook account' : 'Synthetic data'}</SyntheticTag>
          <details><summary>Analysis details</summary><p>{String(social.analysisMode ?? 'FIXTURE')} · {String(social.provider ?? 'FIXTURE')} · {String(social.modelVersion ?? 'no model call')}</p>{Array.isArray(social.limitations) && <p className="mt-2">{(social.limitations as string[]).join(' ')}</p>}</details>
          {social.status === 'AI_UNAVAILABLE' && <Notice tone="warning">AI unavailable; fixture metrics remain visible.</Notice>}
          <p className="text-sm text-gray-700">{String(social.summary ?? 'No report yet.')}</p>
          <dl className="grid grid-cols-2 gap-2 sm:grid-cols-3 text-center text-sm">{Object.entries(metrics).map(([label, value]) => <div key={label} className="rounded-lg bg-gray-50 p-3"><dt className="text-xs text-gray-500">{label.replace(/[A-Z]/g, (c) => ` ${c.toLowerCase()}`)}</dt><dd className="font-semibold">{String(value)}</dd></div>)}</dl>
          <ul className="space-y-2">{findings.map((finding, index) => <li key={index} className="rounded-lg border border-gray-100 p-3 text-sm"><strong>{String(finding.label)}</strong><p className="mt-1 text-gray-600">{String(finding.observation)}</p><details className="mt-2"><summary>Evidence and confidence</summary><p className="mt-1 text-xs text-gray-500">Evidence: {Array.isArray(finding.evidenceIds) ? finding.evidenceIds.join(', ') : '—'} · {String(finding.confidence)}</p>
            {Array.isArray(finding.evidenceIds) ? <ul className="mt-1 space-y-0.5 text-xs text-gray-500">{(finding.evidenceIds as string[]).slice(0, 4).map((id) => <li key={id}><code>{id}</code> {String(evidence.get(id)?.excerpt ?? '')}</li>)}</ul> : null}</details></li>)}</ul>

        </div>}
      </section>
    </div>
    {error && <div className="mt-4"><Notice tone="error">{error}</Notice></div>}
  </Card>;
}

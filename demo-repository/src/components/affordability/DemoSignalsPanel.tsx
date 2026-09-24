import { useState } from 'react';
import { meApi } from '../../api/rocket';
import type { DemoSignalReport, DemoSignalSettings } from '../../api/types';
import { errorMessage, useApi } from '../../hooks/useApi';
import { Card, Loading, Notice } from '../app/Ui';

const locations = ['regular-week', 'sparse', 'contradictory'];
const socialScenarios = ['ordinary', 'repetitive', 'inconsistent', 'sparse', 'injection'];

export function DemoSignalsPanel() {
  const settings = useApi(() => meApi.demoSignalSettings(), []);
  const locationReports = useApi(() => meApi.demoSignalReports('LOCATION'), []);
  const socialReports = useApi(() => meApi.demoSignalReports('SOCIAL'), []);
  const [locationScenario, setLocationScenario] = useState(locations[0]);
  const [socialScenario, setSocialScenario] = useState(socialScenarios[0]);
  const [locationResult, setLocationResult] = useState<DemoSignalReport | null>(null);
  const [socialResult, setSocialResult] = useState<DemoSignalReport | null>(null);
  const [busy, setBusy] = useState('');
  const [error, setError] = useState('');

  const saveToggle = async (key: 'locationEnabled' | 'socialEnabled', enabled: boolean) => {
    if (settings.status !== 'ready') return;
    setBusy(key); setError('');
    try {
      await meApi.updateDemoSignalSettings({ locationEnabled: key === 'locationEnabled' ? enabled : settings.data.locationEnabled,
        socialEnabled: key === 'socialEnabled' ? enabled : settings.data.socialEnabled });
      settings.reload();
    } catch (cause) { setError(errorMessage(cause instanceof Error ? cause : new Error(String(cause)))); }
    finally { setBusy(''); }
  };

  const run = async (kind: 'LOCATION' | 'SOCIAL') => {
    setBusy(kind); setError('');
    try {
      const result = kind === 'LOCATION' ? await meApi.runLocation(locationScenario) : await meApi.runSocial(socialScenario);
      if (kind === 'LOCATION') { setLocationResult(result.report); locationReports.reload(); }
      else { setSocialResult(result.report); socialReports.reload(); }
    } catch (cause) { setError(errorMessage(cause instanceof Error ? cause : new Error(String(cause)))); }
    finally { setBusy(''); }
  };

  const location = locationResult ?? (locationReports.status === 'ready' ? locationReports.data[0] : null);
  const social = socialResult ?? (socialReports.status === 'ready' ? socialReports.data[0] : null);
  const visits = Array.isArray(location?.visits) ? location.visits as Record<string, unknown>[] : [];
  const findings = Array.isArray(social?.findings) ? social.findings as Record<string, unknown>[] : [];
  const metrics = social?.metrics && typeof social.metrics === 'object' ? social.metrics as Record<string, unknown> : {};

  return <Card title="Optional synthetic activity analysis" className="lg:col-span-3">
    <p className="mb-5 text-sm text-gray-600">These demonstrations use repository-owned synthetic scenarios only. Location and social reports never affect the amount, credit score, or application decision.</p>
    {settings.status === 'loading' && <Loading label="Loading optional-analysis permissions…" />}
    {settings.status === 'error' && <Notice tone="error">{errorMessage(settings.error)}</Notice>}
    {settings.status === 'ready' && <div className="grid gap-6 lg:grid-cols-2">
      <section className="rounded-xl border border-gray-200 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div><h3 className="font-semibold text-gray-800">Location history</h3><p className="text-xs text-gray-500">Synthetic visits · deterministic analysis</p></div>
          <label className="inline-flex items-center gap-2 text-sm"><input type="checkbox" checked={settings.data.locationEnabled} disabled={busy !== ''} onChange={(e) => saveToggle('locationEnabled', e.target.checked)} />Allow location demo</label>
        </div>
        {settings.data.locationEnabled && <div className="mt-4 flex flex-wrap gap-2">
          <select value={locationScenario} onChange={(e) => setLocationScenario(e.target.value)} aria-label="Location scenario" className="rounded-lg border border-gray-300 px-3 py-2 text-sm">{locations.map((id) => <option key={id} value={id}>{id.replace('-', ' ')}</option>)}</select>
          <button onClick={() => run('LOCATION')} disabled={busy !== ''} className="btn-secondary px-4 py-2 text-sm">{busy === 'LOCATION' ? 'Analyzing…' : 'Run location analysis'}</button>
        </div>}
        {location && <div className="mt-4 space-y-3">
          <div className="grid grid-cols-3 gap-2 text-center text-sm">
            {[['Visits', location.visitCount], ['Districts', location.distinctDistricts], ['Most visited', location.mostVisitedDistrict]].map(([label, value]) => <div key={String(label)} className="rounded-lg bg-gray-50 p-3"><div className="text-xs text-gray-500">{label}</div><strong>{String(value ?? '—')}</strong></div>)}
          </div>
          <div className="rounded-lg bg-teal-50 p-3" aria-label="Schematic map of synthetic visit districts">
            <div className="text-xs font-semibold uppercase tracking-wide text-teal-900">Observed visits · schematic</div>
            <div className="mt-2 flex flex-wrap gap-2">{Array.from(new Set(visits.map((visit) => String(visit.district ?? '')))).filter(Boolean).map((district) => <span key={district} className="rounded-full bg-white px-3 py-1 text-xs text-teal-900">{district}</span>)}</div>
          </div>
          <ol className="max-h-44 space-y-2 overflow-auto text-sm">{visits.map((visit) => <li key={String(visit.id)} className="flex justify-between gap-3 border-b border-gray-100 pb-2"><span>{String(visit.place)} · {String(visit.district)}</span><time className="text-xs text-gray-500">{new Date(String(visit.arrival)).toLocaleString()}</time></li>)}</ol>
          <p className="text-xs text-gray-500">{String(location.limitation ?? '')}</p>
        </div>}
      </section>

      <section className="rounded-xl border border-gray-200 p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div><h3 className="font-semibold text-gray-800">Social activity</h3><p className="text-xs text-gray-500">Synthetic posts, comments, locations and reactions</p></div>
          <label className="inline-flex items-center gap-2 text-sm"><input type="checkbox" checked={settings.data.socialEnabled} disabled={busy !== ''} onChange={(e) => saveToggle('socialEnabled', e.target.checked)} />Allow social demo</label>
        </div>
        {settings.data.socialEnabled && <div className="mt-4 flex flex-wrap gap-2">
          <select value={socialScenario} onChange={(e) => setSocialScenario(e.target.value)} aria-label="Social activity scenario" className="rounded-lg border border-gray-300 px-3 py-2 text-sm">{socialScenarios.map((id) => <option key={id} value={id}>{id}</option>)}</select>
          <button onClick={() => run('SOCIAL')} disabled={busy !== ''} className="btn-secondary px-4 py-2 text-sm">{busy === 'SOCIAL' ? 'Analyzing…' : 'Run social analysis'}</button>
        </div>}
        {social && <div className="mt-4 space-y-3">
          <div className="flex flex-wrap items-center gap-2"><span className="rounded-full bg-purple-100 px-2.5 py-1 text-xs font-semibold text-purple-900">{String(social.analysisMode ?? 'FIXTURE')} · {String(social.provider ?? 'FIXTURE')}</span><span className="text-xs text-gray-500">{String(social.modelVersion ?? 'no model call')}</span></div>
          {social.status === 'AI_UNAVAILABLE' && <Notice tone="warning">AI unavailable; fixture metrics remain visible.</Notice>}
          <p className="text-sm text-gray-700">{String(social.summary ?? 'No report yet.')}</p>
          <dl className="grid grid-cols-3 gap-2 text-center text-sm">{Object.entries(metrics).slice(0, 3).map(([label, value]) => <div key={label} className="rounded-lg bg-gray-50 p-3"><dt className="text-xs text-gray-500">{label.replace(/[A-Z]/g, (c) => ` ${c.toLowerCase()}`)}</dt><dd className="font-semibold">{String(value)}</dd></div>)}</dl>
          <ul className="space-y-2">{findings.map((finding, index) => <li key={index} className="rounded-lg border border-gray-100 p-3 text-sm"><strong>{String(finding.label)}</strong><p className="mt-1 text-gray-600">{String(finding.observation)}</p><p className="mt-1 text-xs text-gray-500">Evidence: {Array.isArray(finding.evidenceIds) ? finding.evidenceIds.join(', ') : '—'} · {String(finding.confidence)}</p></li>)}</ul>
          {Array.isArray(social.limitations) && <p className="text-xs text-gray-500">{(social.limitations as string[]).join(' ')}</p>}
        </div>}
      </section>
    </div>}
    {error && <div className="mt-4"><Notice tone="error">{error}</Notice></div>}
  </Card>;
}

import { useEffect, useState } from 'react';
import { ApiError } from '../../api/client';
import { meApi } from '../../api/rocket';
import type { HousingSituation, HousingState, HousingVerification } from '../../api/types';
import { errorMessage, useApi } from '../../hooks/useApi';
import { Loading, Notice } from '../app/Ui';
import LocalDropdown from '../common/LocalDropdown';
import { HOUSING_DOCUMENTS, HOUSING_LABELS } from './housing';

/** Housing situation inside "Address and living costs": read-only until Edit, and changed only with a checked document. */
export function HousingSituationPanel({ addressSaved, refreshKey, onUpdated }: { addressSaved: boolean; refreshKey: string; onUpdated: () => void }) {
  const state = useApi(() => meApi.housing(), [refreshKey]);
  const [editing, setEditing] = useState(false);
  const [choice, setChoice] = useState<HousingSituation>('RENTING');
  const [file, setFile] = useState<File | null>(null);
  const [checking, setChecking] = useState(false);
  const [error, setError] = useState('');
  const [result, setResult] = useState<HousingVerification | null>(null);
  // Keep the last loaded state while reloading, so the open form (and its chosen file) is not unmounted.
  const [current, setCurrent] = useState<HousingState | null>(null);
  const required = HOUSING_DOCUMENTS[choice];

  useEffect(() => { if (state.status === 'ready') setCurrent(state.data); }, [state.status, state.data]);
  useEffect(() => { setFile(null); }, [choice]);

  const startEditing = () => {
    if (!current) return;
    setChoice(current.housingSituation === 'RENTING' ? 'OWNER' : 'RENTING');
    setFile(null); setError(''); setResult(null); setEditing(true);
  };

  const check = async (evidence: File | null) => {
    if (!current) return;
    if (!evidence) { setError('Choose a document image first.'); return; }
    if (!['image/png', 'image/jpeg'].includes(evidence.type) || evidence.size > 5 * 1024 * 1024) {
      setError('Choose a PNG or JPEG image smaller than 5 MB.'); return;
    }
    setChecking(true); setError(''); setResult(null);
    try {
      const outcome = await meApi.changeHousing(current.financialRevision, choice, evidence);
      setResult(outcome);
      if (outcome.status === 'VERIFIED_DEMO') { setEditing(false); onUpdated(); }
      state.reload();
    } catch (cause) {
      setError(cause instanceof ApiError && cause.code === 'REVISION_CONFLICT'
        ? 'Your details changed in another session. Reload the page and try again.'
        : errorMessage(cause instanceof Error ? cause : new Error(String(cause))));
    } finally { setChecking(false); }
  };

  const useSample = () => void fetch(required.sample).then(response => response.blob())
    .then(blob => check(new File([blob], `synthetic-${choice.toLowerCase()}-document.png`, { type: 'image/png' })));

  return <section aria-labelledby="housing-situation-heading" className="mt-5 rounded-md border border-gray-200 p-4">
    {!current ? state.status === 'error' ? <Notice tone="error">{errorMessage(state.error)}</Notice> : <Loading label="Loading housing situation…" /> : <>
        <div className="flex flex-wrap items-start justify-between gap-3">
          <div>
            <h3 id="housing-situation-heading" className="font-semibold text-gray-800">Housing situation</h3>
            <p className="mt-1 text-sm text-gray-700"><strong>{HOUSING_LABELS[current.housingSituation]}</strong>
              <span className="text-gray-500"> · {current.verified && current.verifiedAt
                ? `verified with a document on ${new Date(current.verifiedAt).toLocaleDateString()}` : 'not verified by a document'}</span></p>
          </div>
          {!editing && <button type="button" onClick={startEditing} disabled={!addressSaved} className="btn-secondary px-4 py-2 text-sm disabled:opacity-50">Edit</button>}
        </div>
        {!addressSaved && <p className="mt-2 text-xs text-gray-500">Save your address first. The document must show your name and that address.</p>}
        {editing && <div className="mt-4 space-y-4">
          <div><label htmlFor="housing-situation" className="block text-sm font-medium text-gray-700">New housing situation</label>
            <LocalDropdown id="housing-situation" value={choice} onValueChange={value => setChoice(value as HousingSituation)} className="mt-1 w-full max-w-xs"
              options={(Object.keys(HOUSING_LABELS) as HousingSituation[]).filter(value => value !== current.housingSituation)
                .map(value => ({ value, label: HOUSING_LABELS[value] }))} />
          </div>
          <p className="text-sm text-gray-600">To save this change, upload {required.name} that shows your name, your saved address
            and postal code (PNG or JPEG, up to 5 MB). It is checked automatically; only a verified document changes your housing situation.</p>
          <p className="text-xs text-gray-500">The image is sent to the Jev AI model (TypeSafe) only to check these details, then deleted. Only the result is kept.</p>
          <details><summary>View synthetic sample</summary><img src={required.sample} alt={required.alt} className="mt-2 w-full max-w-sm rounded-md border border-gray-200" />
            <p className="mt-2 text-xs text-gray-500">A synthetic sample for Riley Review; it is checked the same way as your own document.</p></details>
          <label className="block text-sm font-medium text-gray-700">Choose document
            <input type="file" accept="image/png,image/jpeg" onChange={(e) => setFile(e.target.files?.[0] ?? null)} className="mt-1 block w-full min-w-0 text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-gray-100 file:px-3 file:py-2" />
          </label>
          {file && <p className="text-xs text-gray-500">Selected: {file.name} · {(file.size / 1024).toFixed(0)} KB</p>}
          <div className="flex flex-wrap gap-2">
            <button type="button" disabled={checking || !file} onClick={() => check(file)} className="btn-primary px-4 py-2 text-sm disabled:opacity-50">{checking ? 'Checking…' : 'Check and save'}</button>
            <button type="button" disabled={checking} onClick={useSample} className="btn-secondary px-4 py-2 text-sm disabled:opacity-60">Use the sample document</button>
            <button type="button" disabled={checking} onClick={() => { setEditing(false); setError(''); }} className="px-4 py-2 text-sm font-medium text-gray-600 hover:text-gray-900">Cancel</button>
          </div>
        </div>}
        {error && <div className="mt-4"><Notice tone="error">{error}</Notice></div>}
        {result && <div className="mt-4"><Notice tone={result.status === 'VERIFIED_DEMO' ? 'info' : result.status === 'MISMATCH' ? 'error' : 'warning'}>
          <strong>{result.status === 'VERIFIED_DEMO' ? `Housing situation changed to ${HOUSING_LABELS[result.housingSituation]}. Your amount is being recalculated.`
            : result.status === 'MISMATCH' ? 'The document does not support this change.' : 'The document needs a manual review.'}</strong>
          {' '}{result.checks.slice(1, result.status === 'VERIFIED_DEMO' ? 2 : -1).join(' · ')}
        </Notice></div>}
      </>}
  </section>;
}

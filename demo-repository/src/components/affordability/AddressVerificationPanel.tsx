import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { meApi } from '../../api/rocket';
import type { Address, AddressSaveRequest } from '../../api/types';
import { errorMessage, useApi } from '../../hooks/useApi';
import { Card, Loading, Notice } from '../app/Ui';
import { formatCurrency } from '../../utils/format';
import sampleAddressCard from '../../assets/sample-address-card-riley.png';

export function AddressVerificationPanel({ onUpdated }: { onUpdated: () => void }) {
  const addressState = useApi(() => meApi.address(), []);
  const districtsState = useApi(() => meApi.districts(), []);
  const [revision, setRevision] = useState(0);
  const [districtId, setDistrictId] = useState('budapest-v');
  const [postalCode, setPostalCode] = useState('');
  const [street, setStreet] = useState('');
  const [building, setBuilding] = useState('');
  const [unit, setUnit] = useState('');
  const [evidenceFile, setEvidenceFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [researching, setResearching] = useState(false);
  const [costExtraction, setCostExtraction] = useState<import('../../api/types').LocalCostExtraction | null>(null);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const current: Address | null = addressState.status === 'ready' ? addressState.data : null;
  const districts = districtsState.status === 'ready' ? districtsState.data : [];
  const selected = districts.find((item) => item.districtId === districtId) ?? districts[0];

  useEffect(() => {
    if (!current?.available) return;
    setRevision(current.revision); setDistrictId(current.districtId ?? 'budapest-v');
    setPostalCode(current.postalCode ?? ''); setStreet(current.street ?? '');
    setBuilding(current.building ?? ''); setUnit(current.unit ?? '');
  }, [addressState.status, current?.revision]);

  const save = async (event: FormEvent) => {
    event.preventDefault(); setError(''); setNotice(''); setSaving(true);
    if (!selected) { setError('The district catalog is unavailable.'); setSaving(false); return; }
    try {
      const body: AddressSaveRequest = { expectedRevision: revision, countryCode: selected.countryCode,
        city: selected.city, districtId: selected.districtId, postalCode, street, building, unit: unit || null };
      const saved = await meApi.saveAddress(body);
      setRevision(saved.address.revision);
      setNotice('Address saved. Verification is reset and the estimate is recalculating without district references.');
      addressState.reload(); onUpdated();
    } catch (cause) {
      setError(cause instanceof ApiError && cause.code === 'REVISION_CONFLICT'
        ? 'This address changed in another session. Reload it before saving.'
        : errorMessage(cause instanceof Error ? cause : new Error(String(cause))));
    } finally { setSaving(false); }
  };

  const verify = async (file: File | null = evidenceFile) => {
    if (!current?.available) return;
    if (!file) { setError('Choose an address-card image first.'); return; }
    if (!['image/png', 'image/jpeg'].includes(file.type) || file.size > 5 * 1024 * 1024) {
      setError('Choose a PNG or JPEG image smaller than 5 MB.'); return;
    }
    setError(''); setNotice(''); setVerifying(true);
    try {
      const result = await meApi.uploadAddressEvidence(current.revision, file);
      setNotice(`${result.status.replaceAll('_', ' ')} · ${result.checks.join(' · ')}`);
      addressState.reload(); onUpdated();
    } catch (cause) {
      setError(errorMessage(cause instanceof Error ? cause : new Error(String(cause))));
    } finally { setVerifying(false); }
  };

  const researchCosts = async () => {
    if (!selected) return;
    setError(''); setResearching(true); setCostExtraction(null);
    try { setCostExtraction(await meApi.researchLocalCosts(selected.districtId)); }
    catch (cause) { setError(errorMessage(cause instanceof Error ? cause : new Error(String(cause)))); }
    finally { setResearching(false); }
  };

  return <Card title="Living address and local cost context" className="lg:col-span-3">
    {addressState.status === 'loading' || districtsState.status === 'loading' ? <Loading label="Loading address and Budapest district references…" /> :
      addressState.status === 'error' ? <Notice tone="error">{errorMessage(addressState.error)}</Notice> :
      districtsState.status === 'error' ? <Notice tone="error">{errorMessage(districtsState.error)}</Notice> : <>
        <div className="grid gap-6 lg:grid-cols-[1fr_0.8fr]">
          <div>
            <p className="mb-4 text-sm text-gray-600">Enter your current Budapest address. Editing any field creates a revision and clears the prior demo verification.</p>
            <form onSubmit={save} className="space-y-4">
              <label className="block text-sm font-medium text-gray-700">District
                <select value={districtId} onChange={(e) => setDistrictId(e.target.value)} required className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2">
                  {districts.map((d) => <option key={d.districtId} value={d.districtId}>{d.displayName}</option>)}
                </select>
              </label>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="text-sm font-medium text-gray-700">Postal code<input required maxLength={20} value={postalCode} onChange={(e) => setPostalCode(e.target.value)} className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2" /></label>
                <label className="text-sm font-medium text-gray-700">Street<input required maxLength={160} value={street} onChange={(e) => setStreet(e.target.value)} className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2" /></label>
                <label className="text-sm font-medium text-gray-700">Building<input required maxLength={40} value={building} onChange={(e) => setBuilding(e.target.value)} className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2" /></label>
                <label className="text-sm font-medium text-gray-700">Unit (optional)<input maxLength={40} value={unit} onChange={(e) => setUnit(e.target.value)} className="mt-1 w-full rounded-lg border border-gray-300 px-3 py-2" /></label>
              </div>
              <div className="flex flex-wrap items-center gap-3">
                <button disabled={saving} className="btn-gradient px-4 py-2 text-sm disabled:opacity-60">{saving ? 'Saving…' : 'Save address'}</button>
                <span className="text-sm text-gray-600">Status: <strong>{current?.verificationStatus ?? 'UNVERIFIED'}</strong></span>
              </div>
            </form>
      {current?.available && <div className="mt-5 rounded-xl border border-dashed border-gray-300 p-4">
          <h3 className="font-semibold text-gray-800">Address evidence image</h3>
          <p className="mt-1 text-sm text-gray-600">PNG/JPEG, maximum 5 MB. The file is processed from a temporary private copy and deleted after review. Only the prepared synthetic sample can be sent to Gemini.</p>
          <div className="mt-3 grid gap-4 md:grid-cols-[220px_1fr] md:items-center">
            <img src={sampleAddressCard} alt="Synthetic address card for Riley Review, Minta utca 12, Budapest 1051, District V. It is marked sample only and not a real document." className="w-full rounded-lg border border-gray-200" />
            <div className="space-y-3">
              <label className="block text-sm font-medium text-gray-700">Choose image
                <input type="file" accept="image/png,image/jpeg" onChange={(e) => setEvidenceFile(e.target.files?.[0] ?? null)} className="mt-1 block w-full text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-gray-100 file:px-3 file:py-2" />
              </label>
              {evidenceFile && <p className="text-xs text-gray-500">Selected: {evidenceFile.name} · {(evidenceFile.size / 1024).toFixed(0)} KB</p>}
              <div className="flex flex-wrap gap-2">
                <button type="button" disabled={verifying} onClick={() => void fetch(sampleAddressCard).then((response) => response.blob()).then((blob) => verify(new File([blob], 'synthetic-address-card.png', { type: 'image/png' })))} className="btn-secondary px-4 py-2 text-sm disabled:opacity-60">{verifying ? 'Reviewing…' : 'Review prepared sample'}</button>
                <button type="button" disabled={verifying || !evidenceFile} onClick={() => verify()} className="btn-secondary px-4 py-2 text-sm disabled:opacity-50">Review selected image</button>
              </div>
              <p className="text-xs text-gray-500">A different or unclear image becomes NEEDS_REVIEW. VERIFIED_DEMO requires the prepared sample’s addressee and all address fields to match.</p>
            </div>
          </div>
        </div>}
          </div>
          <aside className="rounded-xl bg-gray-50 p-5">
            <div className="flex items-start justify-between gap-3"><div><h3 className="font-semibold text-gray-800">District reference costs</h3><p className="text-xs text-gray-500">{selected?.displayName ?? 'Select a district'}</p></div><span className="rounded-full bg-amber-100 px-2 py-1 text-xs font-semibold text-amber-900">Synthetic</span></div>
            {selected && <>
              <dl className="mt-4 space-y-3 text-sm">
                <div className="flex justify-between"><dt className="text-gray-600">Monthly rent, per person</dt><dd className="font-medium">{formatCurrency(selected.monthlyRent)}</dd></div>
                <div className="flex justify-between"><dt className="text-gray-600">Monthly groceries, per person</dt><dd className="font-medium">{formatCurrency(selected.monthlyGroceries)}</dd></div>
                <div className="flex justify-between"><dt className="text-gray-600">City salary reference</dt><dd className="font-medium">{formatCurrency(selected.citySalaryMonthly)} ({selected.salaryBasis.toLowerCase()})</dd></div>
              </dl>
              <p className="mt-4 text-xs text-gray-500">Source: {selected.sourceLabel}; observed {selected.observedAt}; dataset {selected.datasetVersion}. Salary is context only and never used as your income.</p>
              <p className="mt-2 text-xs text-gray-500">District costs apply only after VERIFIED_DEMO. This is a classroom fixture dataset, not Hungarian market research.</p>
              <button type="button" onClick={researchCosts} disabled={researching} className="mt-4 rounded-lg border border-teal-700 px-3 py-2 text-sm font-medium text-teal-800 disabled:opacity-60">{researching ? 'Extracting source…' : 'Run local-cost source extraction'}</button>
              {costExtraction && <div className="mt-3 rounded-lg border border-teal-100 bg-white p-3 text-sm">
                <div className="font-medium">{costExtraction.analysisMode} · {costExtraction.provider}{costExtraction.modelVersion ? ` · ${costExtraction.modelVersion}` : ''}</div>
                {costExtraction.status === 'AI_UNAVAILABLE' ? <p className="mt-1 text-amber-800">AI unavailable. No extracted values were returned; the existing synthetic catalog remains active.</p> : <>
                  <p className="mt-1">Rent {formatCurrency(costExtraction.monthlyRent ?? 0)} · groceries {formatCurrency(costExtraction.monthlyGroceries ?? 0)} · salary context {formatCurrency(costExtraction.citySalaryMonthly ?? 0)} ({costExtraction.salaryBasis?.toLowerCase()})</p>
                  <ul className="mt-2 list-disc pl-5 text-xs text-gray-600">{costExtraction.evidencePassages.map((passage, index) => <li key={index}>{passage}</li>)}</ul>
                </>}
                <p className="mt-2 text-xs text-gray-500">Extraction is a review preview only; publication is a separate backend operation.</p>
              </div>}
            </>}
          </aside>
        </div>
        {(error || notice) && <div className="mt-4">{error ? <Notice tone="error">{error}</Notice> : <Notice>{notice}</Notice>}</div>}
      </>}
  </Card>;
}

import { useEffect, useState, type FormEvent } from 'react';
import { ApiError } from '../../api/client';
import { meApi } from '../../api/rocket';
import type { Address, AddressSaveRequest, PostalCodeDistrict } from '../../api/types';
import { errorMessage, useApi } from '../../hooks/useApi';
import { Card, Loading, Notice } from '../app/Ui';
import { Icon } from '../common/Icon';
import { MonthlyLivingCosts } from './MonthlyLivingCosts';
import sampleAddressCard from '../../assets/sample-address-card-riley.png';

export function AddressVerificationPanel({ onUpdated, refreshKey = 0 }: { onUpdated: () => void; refreshKey?: number }) {
  const addressState = useApi(() => meApi.address(), []);
  const [revision, setRevision] = useState(0);
  const [selected, setSelected] = useState<PostalCodeDistrict | null>(null);
  const [postalError, setPostalError] = useState('');
  const [resolving, setResolving] = useState(false);
  const [postalCode, setPostalCode] = useState('');
  const [street, setStreet] = useState('');
  const [building, setBuilding] = useState('');
  const [unit, setUnit] = useState('');
  const [evidenceFile, setEvidenceFile] = useState<File | null>(null);
  const [saving, setSaving] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const current: Address | null = addressState.status === 'ready' ? addressState.data : null;

  useEffect(() => {
    if (!current?.available) return;
    setRevision(current.revision);
    setPostalCode(current.postalCode ?? ''); setStreet(current.street ?? '');
    setBuilding(current.building ?? ''); setUnit(current.unit ?? '');
  }, [addressState.status, current?.revision]);

  useEffect(() => {
    let cancelled = false;
    setSelected(null); setPostalError(''); setResolving(false);
    if (!/^[0-9]{4}$/.test(postalCode)) return;
    setResolving(true);
    meApi.postalCodeDistrict(postalCode)
      .then(value => { if (!cancelled) setSelected(value); })
      .catch(cause => { if (!cancelled) setPostalError(errorMessage(cause)); })
      .finally(() => { if (!cancelled) setResolving(false); });
    return () => { cancelled = true; };
  }, [postalCode]);

  const save = async (event: FormEvent) => {
    event.preventDefault(); setError(''); setNotice(''); setSaving(true);
    if (!selected || selected.postalCode !== postalCode) { setError('Enter a supported Budapest postal code.'); setSaving(false); return; }
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

  return <Card icon="pin" title="Living address and local cost context" className="lg:col-span-3">
    {addressState.status === 'loading' ? <Loading label="Loading address and Budapest district references…" /> :
      addressState.status === 'error' ? <Notice tone="error">{errorMessage(addressState.error)}</Notice> :
      <>
        <div className="grid gap-6 xl:grid-cols-[1fr_0.8fr]">
          <div>
            <p className="mb-4 text-sm text-gray-600">Save your Budapest address. Changes reset verification.</p>
            <form onSubmit={save} className="space-y-4">
              <label className="block text-sm font-medium text-gray-700">District determined by postal code
                <input readOnly value={selected?.postalCode === postalCode ? selected.displayName : resolving ? 'Looking up postcode…' : 'Enter a postal code'} className="mt-1 w-full rounded-lg border border-gray-200 bg-gray-50 px-3 py-2 text-gray-600" />
              </label>
              <div className="grid gap-4 sm:grid-cols-2">
                <label className="text-sm font-medium text-gray-700">Postal code<input required maxLength={4} pattern="[0-9]{4}" inputMode="numeric" value={postalCode} onChange={(e) => setPostalCode(e.target.value)} className="mt-1 w-full rounded-lg min-h-11 border border-gray-400 px-3 py-2" /></label>
                <label className="text-sm font-medium text-gray-700">Street<input required maxLength={160} value={street} onChange={(e) => setStreet(e.target.value)} className="mt-1 w-full rounded-lg min-h-11 border border-gray-400 px-3 py-2" /></label>
                <label className="text-sm font-medium text-gray-700">Building<input required maxLength={40} value={building} onChange={(e) => setBuilding(e.target.value)} className="mt-1 w-full rounded-lg min-h-11 border border-gray-400 px-3 py-2" /></label>
                <label className="text-sm font-medium text-gray-700">Unit (optional)<input maxLength={40} value={unit} onChange={(e) => setUnit(e.target.value)} className="mt-1 w-full rounded-lg min-h-11 border border-gray-400 px-3 py-2" /></label>
              </div>
              {postalError && <Notice tone="error">{postalError}</Notice>}
              <div className="flex flex-wrap items-center gap-3">
                <button disabled={saving || resolving || !selected || selected.postalCode !== postalCode} className="btn-primary px-4 py-2 text-sm disabled:opacity-60"><Icon name="check" />{saving ? 'Saving…' : 'Save address'}</button>
                <span className="text-sm text-gray-600">Status: <strong>{({UNVERIFIED:'Not verified',VERIFIED_DEMO:'Verified demo',NEEDS_REVIEW:'Needs review',MISMATCH:'Address mismatch'} as const)[current?.verificationStatus ?? 'UNVERIFIED']}</strong></span>
              </div>
            </form>
      {current?.available && <div className="mt-5 rounded-md border border-dashed border-gray-300 p-4">
          <h3 className="font-semibold text-gray-800">Address evidence image</h3>
          <p className="mt-1 text-sm text-gray-600">PNG/JPEG, maximum 5 MB. Use the synthetic sample to explore verification.</p>
          <div className="mt-3 space-y-4">
            <details><summary>View synthetic sample</summary><img src={sampleAddressCard} alt="Synthetic address card for Riley Review, Minta utca 12, Budapest 1051, District V. It is marked sample only and not a real document." className="w-full max-w-sm rounded-md border border-gray-200" /><p className="mt-2 text-xs text-gray-500">Only the prepared synthetic sample can be sent to Gemini. Other images use a temporary private copy, deleted after review.</p></details>
            <div className="space-y-3">
              <label className="block text-sm font-medium text-gray-700">Choose image
                <input type="file" accept="image/png,image/jpeg" onChange={(e) => setEvidenceFile(e.target.files?.[0] ?? null)} className="mt-1 block w-full min-w-0 text-sm file:mr-3 file:rounded-lg file:border-0 file:bg-gray-100 file:px-3 file:py-2" />
              </label>
              {evidenceFile && <p className="text-xs text-gray-500">Selected: {evidenceFile.name} · {(evidenceFile.size / 1024).toFixed(0)} KB</p>}
              <div className="flex flex-wrap gap-2">
                <button type="button" disabled={verifying} onClick={() => void fetch(sampleAddressCard).then((response) => response.blob()).then((blob) => verify(new File([blob], 'synthetic-address-card.png', { type: 'image/png' })))} className="btn-secondary px-4 py-2 text-sm disabled:opacity-60">{verifying ? 'Reviewing…' : 'Review prepared sample'}</button>
                <button type="button" disabled={verifying || !evidenceFile} onClick={() => verify()} className="btn-secondary px-4 py-2 text-sm disabled:opacity-50">Review selected image</button>
              </div>
            </div>
          </div>
        </div>}
          </div>
          <MonthlyLivingCosts postalCode={postalCode} resolved={!!selected && selected.postalCode === postalCode}
            savedAddressMatches={!!current?.available && current.postalCode === postalCode && current.districtId === selected?.districtId}
            refreshKey={`${revision}:${current?.verificationStatus}:${refreshKey}`} onUpdated={onUpdated} />
        </div>
        {(error || notice) && <div className="mt-4">{error ? <Notice tone="error">{error}</Notice> : <Notice>{notice}</Notice>}</div>}
      </>}
  </Card>;
}

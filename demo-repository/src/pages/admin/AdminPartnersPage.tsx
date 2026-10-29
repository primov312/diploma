// AI-assisted (Claude), 2026; reviewed by the author.
import { useState, type FormEvent } from 'react';
import { adminApi, type AdminPartner } from '../../api/staff';
import { Card, Loading, Notice, Page } from '../../components/app/Ui';
import { DataTable, Money, Pill, Td } from '../../components/app/Staff';
import { errorMessage, useApi } from '../../hooks/useApi';

const PartnerEditor = ({ partner, onDone }: { partner: AdminPartner; onDone: (message: string) => void }) => {
  const [name, setName] = useState(partner.displayName);
  const [cap, setCap] = useState(partner.amountCap.toFixed(2));
  const [active, setActive] = useState(partner.active);
  const [error, setError] = useState<string | null>(null);
  const capValid = /^\d+(\.\d{1,2})?$/.test(cap.trim()) && Number(cap) > 0;
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (!name.trim() || !capValid) return;
    setError(null);
    try { await adminApi.updatePartner(partner.id, { displayName: name.trim(), amountCap: Number(cap), active }); onDone(`${name.trim()} saved.`); }
    catch (e) { setError(e instanceof Error ? e.message : 'Saving failed.'); }
  };
  return <form onSubmit={submit} className="mt-3 grid gap-3 sm:grid-cols-[1fr_10rem_auto]" noValidate aria-label={`Edit ${partner.displayName}`}>
    <label className="text-xs text-gray-700">Display name<input className="form-input mt-1 w-full" maxLength={120} value={name} onChange={(e) => setName(e.target.value)} /></label>
    <label className="text-xs text-gray-700">Financing limit (USD)<input inputMode="decimal" className="form-input mt-1 w-full" value={cap} onChange={(e) => setCap(e.target.value)}
      aria-invalid={!capValid} /></label>
    <label className="flex min-h-11 items-center gap-2 self-end text-sm"><input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} />Active</label>
    <div className="flex flex-wrap gap-2 sm:col-span-3">
      <button type="submit" className="btn-primary px-3 text-sm" disabled={!name.trim() || !capValid}>Save</button>
      <button type="button" className="btn-secondary px-3 text-sm" onClick={() => onDone('')}>Cancel</button>
      <span className="text-xs text-gray-500">A lower limit does not change existing credits; open offers above it can no longer be accepted.</span>
    </div>
    {error && <span className="sm:col-span-3"><Notice tone="error">{error}</Notice></span>}
  </form>;
};

const AdminPartnersPage = () => {
  const partners = useApi(() => adminApi.partners(), []);
  const [editing, setEditing] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  return <Page title="Partners" subtitle="Stores that offer Rocket Credit. An inactive partner disappears from the marketplace and cannot be financed.">
    {message && <div className="mb-4"><Notice>{message}</Notice></div>}
    <Card>
      {partners.status === 'loading' && <Loading />}
      {partners.status === 'error' && <Notice tone="error">{errorMessage(partners.error)}</Notice>}
      {partners.status === 'ready' && <DataTable caption="Partners" head={['Partner', 'Financing limit', 'Products shown', 'Sellers', 'Status', '']}>
        {partners.data.map((p) => <tr key={p.id}>
          <Td><span className="font-medium text-gray-800">{p.displayName}</span><span className="block text-xs text-gray-500">{p.slug}</span>
            {editing === p.id && <PartnerEditor partner={p} onDone={(text) => { setEditing(null); setMessage(text || null); partners.reload(); }} />}</Td>
          <Td><Money usd={p.amountCap} /></Td>
          <Td>{p.activeProducts} of {p.products}</Td>
          <Td>{p.sellers}</Td>
          <Td><Pill value={p.active ? 'ACTIVE' : 'INACTIVE'} /></Td>
          <Td>{editing !== p.id && <button type="button" className="btn-secondary px-3 text-sm" onClick={() => { setEditing(p.id); setMessage(null); }}
            aria-label={`Edit ${p.displayName}`}>Edit</button>}</Td>
        </tr>)}
      </DataTable>}
    </Card>
  </Page>;
};

export default AdminPartnersPage;

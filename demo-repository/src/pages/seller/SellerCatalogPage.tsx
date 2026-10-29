// AI-assisted (Claude), 2026; reviewed by the author.
import { useState, type FormEvent, type ReactNode } from 'react';
import { sellerApi, type ProductForm, type SellerCatalogue, type SellerProduct } from '../../api/staff';
import { Card, Empty, Loading, Notice, Page } from '../../components/app/Ui';
import { DataTable, Money, Pill, Td } from '../../components/app/Staff';
import { errorMessage, useApi } from '../../hooks/useApi';
import { formatCurrency, formatUsd } from '../../utils/format';

type Draft = { name: string; description: string; category: string; imagePath: string; price: string };
const EMPTY: Draft = { name: '', description: '', category: '', imagePath: '', price: '' };

/** The same rules as the server (SellerService.validate), so most mistakes are caught before sending. */
const validate = (d: Draft, c: SellerCatalogue): Record<string, string> => {
  const errors: Record<string, string> = {};
  if (!d.name.trim() || d.name.trim().length > 160) errors.name = 'Enter a name of 1 to 160 characters.';
  if (!d.description.trim() || d.description.trim().length > 500) errors.description = 'Enter a description of 1 to 500 characters.';
  if (!c.categories.includes(d.category)) errors.category = 'Choose a category.';
  if (!c.images.some((i) => i.path === d.imagePath)) errors.imagePath = 'Choose an image.';
  const price = Number(d.price);
  if (!/^\d+(\.\d{1,2})?$/.test(d.price.trim()) || !(price > 0)) errors.price = 'Enter a price above 0 with at most two decimals.';
  else if (price > c.partner.amountCap) errors.price = `The price cannot be above your financing limit of ${formatUsd(c.partner.amountCap)}.`;
  return errors;
};

const FormField = ({ id, label, error, hint, children }: { id: string; label: string; error?: string; hint?: string; children: ReactNode }) => (
  <div>
    <label htmlFor={id} className="block text-sm font-medium text-gray-700">{label}</label>
    {children}
    {error ? <p id={`${id}-error`} className="mt-1 text-xs text-error-700">{error}</p>
      : hint ? <p id={`${id}-hint`} className="mt-1 text-xs text-gray-500">{hint}</p> : null}
  </div>
);

const ProductEditor = ({ catalogue, editing, onDone }: { catalogue: SellerCatalogue; editing: SellerProduct | null; onDone: () => void }) => {
  const [draft, setDraft] = useState<Draft>(editing ? { name: editing.name, description: editing.description, category: editing.category,
    imagePath: editing.imagePath, price: editing.price.toFixed(2) } : EMPTY);
  const [touched, setTouched] = useState(false);
  const [saving, setSaving] = useState(false);
  const [serverError, setServerError] = useState<string | null>(null);
  const errors = validate(draft, catalogue);
  const shown = touched ? errors : {};
  const set = (key: keyof Draft) => (value: string) => setDraft({ ...draft, [key]: value });
  const described = (id: keyof Draft) => ({ 'aria-invalid': Boolean(shown[id]), 'aria-describedby': shown[id] ? `${id}-error` : `${id}-hint` });

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setTouched(true);
    setServerError(null);
    if (Object.keys(errors).length) return;
    const body: ProductForm = { name: draft.name.trim(), description: draft.description.trim(), category: draft.category,
      imagePath: draft.imagePath, price: Number(draft.price) };
    setSaving(true);
    try {
      await (editing ? sellerApi.update(editing.id, body) : sellerApi.create(body));
      onDone();
    } catch (e) {
      setServerError(e instanceof Error ? e.message : 'Saving failed.');
    } finally {
      setSaving(false);
    }
  };

  const price = Number(draft.price);
  return <Card title={editing ? `Edit “${editing.name}”` : 'Add a product'} icon="store" className="mb-6">
    <form className="grid gap-4 md:grid-cols-2" onSubmit={submit} noValidate>
      <FormField id="name" label="Name" error={shown.name} hint="1 to 160 characters">
        <input id="name" className="form-input mt-1 w-full" value={draft.name} onChange={(e) => set('name')(e.target.value)} maxLength={160} {...described('name')} />
      </FormField>
      <FormField id="price" label="Price (USD)" error={shown.price}
        hint={`Up to ${formatUsd(catalogue.partner.amountCap)}${price > 0 ? ` · customers see ${formatCurrency(price)}` : ''}`}>
        <input id="price" inputMode="decimal" className="form-input mt-1 w-full" value={draft.price} onChange={(e) => set('price')(e.target.value)} {...described('price')} />
      </FormField>
      <FormField id="description" label="Description" error={shown.description} hint={`${draft.description.trim().length} / 500 characters`}>
        <textarea id="description" rows={3} className="form-input mt-1 w-full" value={draft.description} maxLength={500}
          onChange={(e) => set('description')(e.target.value)} {...described('description')} />
      </FormField>
      <div className="grid gap-4">
        <FormField id="category" label="Category" error={shown.category} hint="One of the existing marketplace categories">
          <select id="category" className="form-input mt-1 w-full" value={draft.category} onChange={(e) => set('category')(e.target.value)} {...described('category')}>
            <option value="">Choose…</option>{catalogue.categories.map((c) => <option key={c}>{c}</option>)}
          </select>
        </FormField>
        <FormField id="imagePath" label="Image" error={shown.imagePath} hint="From the existing catalogue images; uploads are not part of this demo">
          <select id="imagePath" className="form-input mt-1 w-full" value={draft.imagePath} onChange={(e) => set('imagePath')(e.target.value)} {...described('imagePath')}>
            <option value="">Choose…</option>{catalogue.images.map((i) => <option key={i.path} value={i.path}>{i.alt.replace('Illustrative photo: ', '')}</option>)}
          </select>
        </FormField>
      </div>
      {draft.imagePath && <img src={draft.imagePath} alt="" className="aspect-[4/3] w-40 rounded border border-gray-200 object-cover" />}
      <div className="flex flex-wrap items-center gap-3 md:col-span-2">
        <button type="submit" className="btn-primary" disabled={saving}>{saving ? 'Saving…' : editing ? 'Save changes' : 'Add product'}</button>
        <button type="button" className="btn-secondary" onClick={onDone}>Cancel</button>
        {editing && <span className="text-xs text-gray-500">A new price applies to new quotes only; existing offers and credits keep their price.</span>}
      </div>
      {serverError && <div className="md:col-span-2"><Notice tone="error">{serverError}</Notice></div>}
    </form>
  </Card>;
};

const SellerCatalogPage = () => {
  const catalogue = useApi(() => sellerApi.catalogue(), []);
  const [editing, setEditing] = useState<SellerProduct | 'new' | null>(null);
  const [busy, setBusy] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const toggle = async (p: SellerProduct) => {
    setBusy(p.id);
    setError(null);
    try { await sellerApi.setActive(p.id, !p.active); catalogue.reload(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Change failed.'); }
    finally { setBusy(null); }
  };

  if (catalogue.status === 'loading') return <Page title="Catalogue"><Loading /></Page>;
  if (catalogue.status === 'error') return <Page title="Catalogue"><Notice tone="error">{errorMessage(catalogue.error)}</Notice></Page>;
  const c = catalogue.data;
  const active = c.items.filter((p) => p.active).length;
  return <Page title={`${c.partner.name} catalogue`} subtitle={`${active} of ${c.items.length} products are shown in the marketplace. Financing limit ${formatUsd(c.partner.amountCap)}.`}
    actions={editing === null && <button type="button" className="btn-primary" onClick={() => setEditing('new')}>Add product</button>}>
    {editing !== null && <ProductEditor key={editing === 'new' ? 'new' : editing.id} catalogue={c} editing={editing === 'new' ? null : editing}
      onDone={() => { setEditing(null); catalogue.reload(); }} />}
    {error && <div className="mb-4"><Notice tone="error">{error}</Notice></div>}
    <Card>
      {c.items.length === 0 ? <Empty>No products yet. Add your first product.</Empty> :
        <DataTable caption="Your products" head={['Product', 'Category', 'Price', 'Status', 'Actions']}>
          {c.items.map((p) => <tr key={p.id}>
            <Td><span className="font-medium text-gray-800">{p.name}</span><span className="block max-w-xs truncate text-xs text-gray-500" title={p.description}>{p.description}</span></Td>
            <Td>{p.category}</Td>
            <Td><Money usd={p.price} /></Td>
            <Td><Pill value={p.active ? 'ACTIVE' : 'INACTIVE'} label={p.active ? 'shown' : 'hidden'} /></Td>
            <Td><span className="flex flex-wrap gap-2">
              <button type="button" className="btn-secondary px-3 text-sm" onClick={() => { setEditing(p); window.scrollTo({ top: 0 }); }}
                aria-label={`Edit ${p.name}`}>Edit</button>
              <button type="button" className="btn-secondary px-3 text-sm" disabled={busy === p.id} onClick={() => toggle(p)}
                aria-label={`${p.active ? 'Deactivate' : 'Activate'} ${p.name}`}>{p.active ? 'Deactivate' : 'Activate'}</button>
            </span></Td>
          </tr>)}
        </DataTable>}
    </Card>
  </Page>;
};

export default SellerCatalogPage;

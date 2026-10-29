// AI-assisted (Claude), 2026; reviewed by the author.
import { useState, type FormEvent } from 'react';
import { useSearchParams } from 'react-router-dom';
import type { Role } from '../../api/auth';
import { adminApi, type AdminUser } from '../../api/staff';
import { useAuth } from '../../auth/AuthContext';
import { Card, Empty, Loading, Notice, Page } from '../../components/app/Ui';
import { DataTable, Pager, Pill, Td, dateTime } from '../../components/app/Staff';
import { errorMessage, useApi } from '../../hooks/useApi';

const SIZE = 25;
const ROLES: Role[] = ['CUSTOMER', 'SELLER', 'ADMIN'];

/** Creates a seller for an existing partner. The password is sent once and never shown again. */
const CreateSeller = ({ onDone }: { onDone: (message: string) => void }) => {
  const partners = useApi(() => adminApi.partners(), []);
  const [form, setForm] = useState({ email: '', displayName: '', partnerId: '', password: '' });
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const set = (key: keyof typeof form) => (value: string) => setForm({ ...form, [key]: value });
  const invalid = !/^\S+@\S+\.\S+$/.test(form.email) || !form.displayName.trim() || !form.partnerId || form.password.length < 8 || form.password.length > 72;
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    if (invalid || saving) return;
    setSaving(true);
    setError(null);
    try {
      const created = await adminApi.createSeller({ email: form.email.trim(), displayName: form.displayName.trim(), partnerId: Number(form.partnerId), password: form.password });
      onDone(`Seller ${created.email} created for ${created.partnerName}. Pass the temporary password on to them.`);
    } catch (e) { setError(e instanceof Error ? e.message : 'The seller was not created.'); }
    finally { setSaving(false); }
  };
  return <Card title="Create a seller account" icon="building" className="mb-6">
    <form className="grid gap-4 sm:grid-cols-2" onSubmit={submit} noValidate>
      <label className="text-sm text-gray-700">E-mail<input type="email" autoComplete="off" className="form-input mt-1 w-full" value={form.email} onChange={(e) => set('email')(e.target.value)} /></label>
      <label className="text-sm text-gray-700">Display name<input className="form-input mt-1 w-full" maxLength={120} value={form.displayName} onChange={(e) => set('displayName')(e.target.value)} /></label>
      <label className="text-sm text-gray-700">Partner
        <select className="form-input mt-1 w-full" value={form.partnerId} onChange={(e) => set('partnerId')(e.target.value)}>
          <option value="">Choose…</option>
          {partners.status === 'ready' && partners.data.map((p) => <option key={p.id} value={p.id}>{p.displayName}{p.active ? '' : ' (inactive)'}</option>)}
        </select>
      </label>
      <label className="text-sm text-gray-700">Temporary password<input type="password" autoComplete="new-password" className="form-input mt-1 w-full" value={form.password}
        onChange={(e) => set('password')(e.target.value)} aria-describedby="seller-password-hint" />
        <span id="seller-password-hint" className="mt-1 block text-xs text-gray-500">8 to 72 characters</span></label>
      <div className="flex flex-wrap gap-3 sm:col-span-2">
        <button type="submit" className="btn-primary" disabled={invalid || saving}>{saving ? 'Creating…' : 'Create seller'}</button>
        <button type="button" className="btn-secondary" onClick={() => onDone('')}>Cancel</button>
      </div>
      {error && <div className="sm:col-span-2"><Notice tone="error">{error}</Notice></div>}
    </form>
  </Card>;
};

const ResetPassword = ({ user, onDone }: { user: AdminUser; onDone: (message: string) => void }) => {
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    try { await adminApi.resetPassword(user.id, password); onDone(`New temporary password set for ${user.email}.`); }
    catch (e) { setError(e instanceof Error ? e.message : 'The password was not changed.'); }
  };
  return <form onSubmit={submit} className="mt-2 flex flex-wrap items-end gap-2" noValidate>
    <label className="text-xs text-gray-700">New temporary password for {user.email}
      <input type="password" autoComplete="new-password" className="form-input mt-1 block w-56" value={password} onChange={(e) => setPassword(e.target.value)} autoFocus />
    </label>
    <button type="submit" className="btn-primary px-3 text-sm" disabled={password.length < 8 || password.length > 72}>Set password</button>
    <button type="button" className="btn-secondary px-3 text-sm" onClick={() => onDone('')}>Cancel</button>
    {error && <span className="w-full"><Notice tone="error">{error}</Notice></span>}
  </form>;
};

const AdminUsersPage = () => {
  const { user: me } = useAuth();
  const [params, setParams] = useSearchParams();
  const role = ROLES.includes(params.get('role') as Role) ? params.get('role')! : '';
  const [q, setQ] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const [creating, setCreating] = useState(false);
  const [resetting, setResetting] = useState<number | null>(null);
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const users = useApi(() => adminApi.users({ role, q: search, page, size: SIZE }), [role, search, page]);

  const done = (text: string) => { setCreating(false); setResetting(null); setMessage(text || null); users.reload(); };
  const [confirming, setConfirming] = useState<number | null>(null);
  const toggle = async (u: AdminUser) => {
    // Disabling takes two clicks: the first asks for confirmation in place.
    if (u.enabled && confirming !== u.id) { setConfirming(u.id); return; }
    setConfirming(null);
    setError(null);
    try { await adminApi.setEnabled(u.id, !u.enabled); setMessage(`${u.email} ${u.enabled ? 'disabled; their open sessions end on their next request' : 'enabled'}.`); users.reload(); }
    catch (e) { setError(e instanceof Error ? e.message : 'Change failed.'); }
  };

  return <Page title="Users" subtitle="Accounts of all three kinds. Roles cannot be changed; sellers are created here, customers sign up themselves."
    actions={!creating && <button type="button" className="btn-primary" onClick={() => { setCreating(true); setMessage(null); }}>Create seller</button>}>
    {creating && <CreateSeller onDone={done} />}
    {message && <div className="mb-4"><Notice>{message}</Notice></div>}
    {error && <div className="mb-4"><Notice tone="error">{error}</Notice></div>}
    <Card>
      <form className="mb-4 grid gap-3 sm:grid-cols-[12rem_1fr_auto]" onSubmit={(e) => { e.preventDefault(); setSearch(q.trim()); setPage(0); }}>
        <label className="text-sm text-gray-700">Role
          <select className="form-input mt-1 w-full" value={role} onChange={(e) => { setParams(e.target.value ? { role: e.target.value } : {}); setPage(0); }}>
            <option value="">All</option>{ROLES.map((r) => <option key={r} value={r}>{r.toLowerCase()}</option>)}
          </select>
        </label>
        <label className="text-sm text-gray-700">Search e-mail or name<input type="search" className="form-input mt-1 w-full" value={q} onChange={(e) => setQ(e.target.value)} /></label>
        <button type="submit" className="btn-secondary self-end">Search</button>
      </form>
      {users.status === 'loading' && <Loading />}
      {users.status === 'error' && <Notice tone="error">{errorMessage(users.error)}</Notice>}
      {users.status === 'ready' && (users.data.items.length === 0 ? <Empty>No accounts match.</Empty> : <>
        <DataTable caption="Accounts" head={['Account', 'Role', 'Partner', 'Status', 'Created', 'Actions']}>
          {users.data.items.map((u) => <tr key={u.id}>
            <Td><span className="font-medium text-gray-800">{u.displayName}</span><span className="block text-xs text-gray-500">{u.email} · #{u.id}</span>
              {resetting === u.id && <ResetPassword user={u} onDone={done} />}</Td>
            <Td>{u.role.toLowerCase()}</Td>
            <Td>{u.partnerName ?? '–'}</Td>
            <Td><Pill value={u.enabled ? 'ENABLED' : 'DISABLED'} /></Td>
            <Td className="whitespace-nowrap">{dateTime(u.createdAt)}</Td>
            <Td><span className="flex flex-wrap gap-2">
              {u.id !== me?.id && <button type="button" className={confirming === u.id ? 'btn-primary px-3 text-sm' : 'btn-secondary px-3 text-sm'} onClick={() => toggle(u)}
                aria-label={`${confirming === u.id ? 'Confirm: disable' : u.enabled ? 'Disable' : 'Enable'} ${u.email}`}>
                {confirming === u.id ? 'Confirm disable' : u.enabled ? 'Disable' : 'Enable'}</button>}
              {confirming === u.id && <button type="button" className="btn-secondary px-3 text-sm" onClick={() => setConfirming(null)}>Keep enabled</button>}
              <button type="button" className="btn-secondary px-3 text-sm" onClick={() => { setResetting(u.id); setMessage(null); }} aria-label={`Reset password of ${u.email}`}>Reset password</button>
            </span></Td>
          </tr>)}
        </DataTable>
        <Pager page={page} size={SIZE} total={users.data.total} onPage={setPage} />
      </>)}
    </Card>
  </Page>;
};

export default AdminUsersPage;

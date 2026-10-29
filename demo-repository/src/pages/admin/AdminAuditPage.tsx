// AI-assisted (Claude), 2026; reviewed by the author.
import { useState, type FormEvent } from 'react';
import { adminApi } from '../../api/staff';
import { Card, Empty, Loading, Notice, Page } from '../../components/app/Ui';
import { DataTable, Pager, Td, dateTime } from '../../components/app/Staff';
import { errorMessage, useApi } from '../../hooks/useApi';

const SIZE = 50;
const ACTIONS = ['ADMIN_SELLER_CREATED', 'ADMIN_USER_DISABLED', 'ADMIN_USER_ENABLED', 'ADMIN_PASSWORD_RESET', 'ADMIN_REVIEW_DECIDED',
  'ADMIN_PARTNER_UPDATED', 'SELLER_PRODUCT_CREATED', 'SELLER_PRODUCT_UPDATED', 'SELLER_PRODUCT_DEACTIVATED', 'SELLER_PRODUCT_ACTIVATED'];

/** Read-only: the audit log has no write endpoint, and the database refuses changes to its rows. */
const AdminAuditPage = () => {
  const [draft, setDraft] = useState({ actor: '', action: '', from: '', to: '' });
  const [filters, setFilters] = useState(draft);
  const [page, setPage] = useState(0);
  const events = useApi(() => adminApi.audit({ ...filters, page, size: SIZE }), [filters, page]);
  const set = (key: keyof typeof draft) => (value: string) => setDraft({ ...draft, [key]: value });
  const apply = (event: FormEvent) => { event.preventDefault(); setFilters(draft); setPage(0); };

  return <Page title="Audit log" subtitle="Every change made by an administrator or a seller, newest first. Entries cannot be edited or deleted.">
    <Card>
      <form className="mb-4 grid gap-3 sm:grid-cols-2 lg:grid-cols-5" onSubmit={apply}>
        <label className="text-sm text-gray-700">Actor e-mail<input type="search" className="form-input mt-1 w-full" value={draft.actor} onChange={(e) => set('actor')(e.target.value)} /></label>
        <label className="text-sm text-gray-700">Action
          <select className="form-input mt-1 w-full" value={draft.action} onChange={(e) => set('action')(e.target.value)}>
            <option value="">All</option>{ACTIONS.map((a) => <option key={a} value={a}>{a.replaceAll('_', ' ').toLowerCase()}</option>)}
          </select>
        </label>
        <label className="text-sm text-gray-700">From<input type="date" className="form-input mt-1 w-full" value={draft.from} onChange={(e) => set('from')(e.target.value)} /></label>
        <label className="text-sm text-gray-700">To<input type="date" className="form-input mt-1 w-full" value={draft.to} onChange={(e) => set('to')(e.target.value)} /></label>
        <button type="submit" className="btn-secondary self-end">Filter</button>
      </form>
      {events.status === 'loading' && <Loading />}
      {events.status === 'error' && <Notice tone="error">{errorMessage(events.error)}</Notice>}
      {events.status === 'ready' && (events.data.items.length === 0 ? <Empty>No entries match.</Empty> : <>
        <DataTable caption="Audit events" head={['When', 'Actor', 'Action', 'Target', 'Details']}>
          {events.data.items.map((e) => <tr key={e.id}>
            <Td className="whitespace-nowrap">{dateTime(e.createdAt)}</Td>
            <Td>{e.actorEmail ?? '–'}<span className="block text-xs text-gray-500">{e.actorRole.toLowerCase()}</span></Td>
            <Td><code className="text-xs">{e.action}</code></Td>
            <Td>{e.targetType.toLowerCase()} {e.targetId ? `#${e.targetId}` : ''}</Td>
            <Td><code className="block max-w-xs break-words text-xs text-gray-600">{Object.keys(e.details).length ? JSON.stringify(e.details) : '–'}</code></Td>
          </tr>)}
        </DataTable>
        <Pager page={page} size={SIZE} total={events.data.total} onPage={setPage} />
      </>)}
    </Card>
  </Page>;
};

export default AdminAuditPage;

// AI-assisted (Claude), 2026: buying and applying limited to customers and visitors; reviewed by the author.
import { useMemo, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { catalogApi } from '../../api/rocket';
import type { CatalogItem } from '../../api/types';
import { useAuth } from '../../auth/AuthContext';
import { Empty, Loading, Modal, Notice, Page } from '../../components/app/Ui';
import BalanceCheckout from '../../components/payments/BalanceCheckout';
import { Icon } from '../../components/common/Icon';
import { errorMessage, useApi } from '../../hooks/useApi';
import { cn } from '../../utils/cn';
import { formatCurrency } from '../../utils/format';

const PAGE = 24;
/** Each seller's catalog forms one department; the finer product categories become a second row. */
const DEPARTMENTS: Record<string, string> = { streambox: 'Entertainment', threadly: 'Fashion', markethub: 'Electronics & home' };
const department = (item: CatalogItem) => DEPARTMENTS[item.partnerSlug] ?? item.partnerName;
const SORTS: Record<string, { label: string; compare: (a: CatalogItem, b: CatalogItem) => number }> = {
  popular: { label: 'Recommended', compare: () => 0 },
  priceAsc: { label: 'Price: low to high', compare: (a, b) => a.price - b.price },
  priceDesc: { label: 'Price: high to low', compare: (a, b) => b.price - a.price },
};

/**
 * One marketplace for every partner's products. Each card names its seller because the
 * financing limit and purchase history belong to the seller; the backend resolves prices,
 * so links carry only the product ID.
 */
const MarketplacePage = () => {
  const catalog = useApi(() => catalogApi.list(), []);
  const { user } = useAuth();
  // Sellers and administrators may browse, but buying and credit are customer actions (the server refuses them too).
  const staff = user !== null && user.role !== 'CUSTOMER';
  const [params, setParams] = useSearchParams();
  const [query, setQuery] = useState('');
  const [limit, setLimit] = useState(PAGE);
  const dept = params.get('department') ?? 'All';
  const category = params.get('category') ?? 'All';
  const sort = SORTS[params.get('sort') ?? ''] ? params.get('sort')! : 'popular';
  const items = catalog.status === 'ready' ? catalog.data : [];
  const paying = items.find((item) => item.id === Number(params.get('pay')));

  const setParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value); else next.delete(key);
    setParams(next, { replace: key !== 'pay' });
    if (key !== 'pay') setLimit(PAGE);
  };

  const departments = useMemo(() => ['All', ...new Set(items.map(department))], [items]);
  const categories = useMemo(() => ['All', ...new Set(items.filter((item) => department(item) === dept).map((item) => item.category))], [items, dept]);
  const visible = useMemo(() => {
    const terms = query.trim().toLowerCase();
    return items
      .filter((item) => (dept === 'All' || department(item) === dept) && (category === 'All' || item.category === category)
        && `${item.name} ${item.description} ${item.partnerName}`.toLowerCase().includes(terms))
      .sort(SORTS[sort].compare);
  }, [items, dept, category, query, sort]);

  return <Page title="Marketplace" subtitle="Pay now from your balance, or spread the cost with Rocket Credit.">
    {catalog.status === 'loading' && <Loading label="Loading products…" />}
    {catalog.status === 'error' && <Notice tone="error">{errorMessage(catalog.error)}</Notice>}
    {catalog.status === 'ready' && <>
      <div className="mb-4 flex flex-wrap items-center gap-3">
        <label className="relative w-full min-w-0 sm:w-auto sm:max-w-sm sm:flex-1">
          <span className="sr-only">Search products</span>
          <Icon name="search" className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-500" />
          <input type="search" value={query} onChange={(event) => { setQuery(event.target.value); setLimit(PAGE); }}
            placeholder="Search products or sellers" className="form-input w-full pl-10" />
        </label>
        <label className="flex items-center gap-2 text-sm text-gray-600">Sort
          <select value={sort} onChange={(event) => setParam('sort', event.target.value === 'popular' ? null : event.target.value)} className="form-input py-1.5">
            {Object.entries(SORTS).map(([value, { label }]) => <option key={value} value={value}>{label}</option>)}
          </select>
        </label>
      </div>
      <div className="-mx-4 mb-3 flex gap-2 overflow-x-auto px-4 pb-1 md:mx-0 md:flex-wrap md:px-0" role="group" aria-label="Department">
        {departments.map((item) => <button key={item} type="button" aria-pressed={dept === item}
          onClick={() => { const next = new URLSearchParams(params); if (item === 'All') next.delete('department'); else next.set('department', item); next.delete('category'); setParams(next, { replace: true }); setLimit(PAGE); }}
          className={cn('min-h-11 shrink-0 rounded-full border px-4 text-sm font-medium transition-colors',
            dept === item ? 'border-primary bg-primary text-white' : 'border-gray-300 bg-white text-gray-700 hover:border-primary')}>{item}</button>)}
      </div>
      {dept !== 'All' && <div className="-mx-4 mb-3 flex gap-2 overflow-x-auto px-4 pb-1 md:mx-0 md:flex-wrap md:px-0" role="group" aria-label="Category">
        {categories.map((item) => <button key={item} type="button" onClick={() => setParam('category', item === 'All' ? null : item)} aria-pressed={category === item}
          className={cn('min-h-11 shrink-0 rounded-full px-3 text-sm transition-colors',
            category === item ? 'bg-primary-100 font-semibold text-primary-900' : 'text-gray-600 hover:bg-gray-100')}>{item === 'All' ? `All ${dept.toLowerCase()}` : item}</button>)}
      </div>}
      <div className="mb-3" />
      <p className="mb-4 text-sm text-gray-500" aria-live="polite">{visible.length} product{visible.length === 1 ? '' : 's'}</p>
      {visible.length === 0 ? <Empty>No products match. Try another word or category.</Empty> : <>
        <ul className="grid grid-cols-2 gap-3 sm:gap-5 md:grid-cols-3 xl:grid-cols-4">
          {visible.slice(0, limit).map((item) => <li key={item.id} className="flex flex-col overflow-hidden rounded-lg border border-gray-200 bg-white">
            <img src={item.imagePath} alt={item.imageAlt} loading="lazy"
              className={cn('aspect-[4/3] w-full bg-gray-100', item.partnerSlug === 'streambox' ? 'object-cover' : 'object-contain')}
              onError={(event) => { if (!event.currentTarget.src.endsWith('/catalog/fallback.svg')) event.currentTarget.src = '/catalog/fallback.svg'; }} />
            <div className="flex flex-1 flex-col p-3 sm:p-4">
              <p className="text-xs text-gray-500">{item.partnerName} · {item.category}</p>
              <h2 className="mt-1 line-clamp-2 font-semibold text-gray-800" title={item.description}>{item.name}</h2>
              <p className="mt-auto pt-3 text-xl font-bold text-gray-900">{formatCurrency(item.price)}</p>
              {staff ? <p className="mt-3 rounded-md border border-dashed border-gray-300 px-3 py-2 text-center text-xs text-gray-500" aria-disabled="true">
                Customers can apply for credit or pay here. You are signed in as a {user.role === 'SELLER' ? 'seller' : 'administrator'}.
              </p> : <div className="mt-3 grid gap-2">
                <Link to={`/apply?product=${item.id}`} className="btn-primary px-3 text-sm">Apply for credit</Link>
                {user
                  ? <button type="button" className="btn-secondary px-3 text-sm" onClick={() => setParam('pay', String(item.id))}>Pay now</button>
                  : <Link className="btn-secondary px-3 text-sm" to="/login" state={{ from: `/marketplace?pay=${item.id}` }}>Pay now</Link>}
              </div>}
            </div>
          </li>)}
        </ul>
        {visible.length > limit && <div className="mt-6 text-center"><button type="button" className="btn-secondary" onClick={() => setLimit(limit + PAGE)}>Show more ({visible.length - limit} left)</button></div>}
      </>}
      <p className="mt-8 text-xs text-gray-500">Illustrative products and prices; sellers do not imply affiliation. <a href="/catalog/credits.html" className="underline">Image credits</a></p>
    </>}
    {user && !staff && paying && <Modal label={`Pay for ${paying.name}`} onClose={() => setParam('pay', null)}>
      <BalanceCheckout key={paying.id} product={paying} partner={paying.partnerSlug} close={() => setParam('pay', null)} />
    </Modal>}
  </Page>;
};

export default MarketplacePage;

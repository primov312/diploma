import { useEffect, useMemo, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { partnersApi } from '../../api/rocket';
import { useAuth } from '../../auth/AuthContext';
import { Empty, Loading, Notice } from '../../components/app/Ui';
import { errorMessage, useApi } from '../../hooks/useApi';
import { cn } from '../../utils/cn';
import { formatCurrency } from '../../utils/format';
import { BRANDS } from './brands';

/**
 * One component renders all three stores. Prices come from the backend catalog; the
 * "Apply with Rocket Credit" link only carries the partner slug and product ID — the
 * backend resolves the price, so nothing in the URL is authoritative.
 */
const StorePage = () => {
  const { slug = '' } = useParams();
  const brand = BRANDS[slug];
  const { user } = useAuth();
  const partner = useApi(() => partnersApi.get(slug), [slug]);
  const [category, setCategory] = useState('All');
  const [query, setQuery] = useState('');
  const products = partner.status === 'ready' ? partner.data.products : [];
  const categories = useMemo(() => ['All', ...new Set(products.map((item) => item.category))], [products]);
  const visibleProducts = useMemo(() => products.filter((item) => {
    const matchesCategory = category === 'All' || item.category === category;
    const terms = `${item.name} ${item.description}`.toLowerCase();
    return matchesCategory && terms.includes(query.trim().toLowerCase());
  }), [products, category, query]);

  useEffect(() => {
    setCategory('All');
    setQuery('');
  }, [slug]);

  if (!brand) {
    return (
      <div className="bg-gradient-hero py-24 text-center">
        <h1 className="text-3xl font-bold text-gray-800">Store not found</h1>
        <Link to="/stores" className="mt-4 inline-block text-primary">All demo stores</Link>
      </div>
    );
  }

  return (
    <div>
      <section className={cn('py-16', brand.accent, brand.text)}>
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-3 text-sm opacity-80">
            <Link to="/stores" className="underline-offset-2 hover:underline">Demo stores</Link>
            <span aria-hidden="true">›</span>
            <span>{brand.category}</span>
          </div>
          <h1 className="mt-4 text-4xl font-black tracking-tight sm:text-5xl">{brand.wordmark}</h1>
          <p className="mt-3 max-w-2xl text-lg opacity-90">{brand.tagline}</p>
          <p className="mt-6 inline-flex items-center gap-2 rounded-full bg-black/10 px-3 py-1 text-sm">
            Demo financing by <strong>Rocket Credit</strong>
            {partner.data && <span>· up to {formatCurrency(partner.data.amountCap)} per request</span>}
          </p>
        </div>
      </section>

      <section className="bg-secondary-50 py-12">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
          <p className="mb-5 max-w-3xl text-gray-600">
            {brand.blurb} This is a demonstration store inside the Rocket Credit app: there is no cart, checkout or
            payment. “Apply with Rocket Credit” opens a financing request for the product{user ? '' : ' after you sign in'}.
          </p>
          <p className="mb-8 max-w-3xl text-sm text-gray-500">Stock photos are illustrative. This academic demo is not affiliated with {brand.name}; names, items and prices do not represent live offers.</p>

          {partner.status === 'loading' && <Loading label="Loading catalog…" />}
          {partner.status === 'error' && <Notice tone="error">{errorMessage(partner.error)}</Notice>}
          {partner.status === 'ready' && partner.data.products.length === 0 && <Empty>No products in this catalog.</Empty>}
          {partner.status === 'ready' && products.length > 0 && <>
            <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
              <div className="flex flex-wrap gap-2" role="group" aria-label="Product category">
                {categories.map((item) => (
                  <button key={item} type="button" onClick={() => setCategory(item)}
                    aria-pressed={category === item}
                    className={cn('rounded-full border px-3 py-1.5 text-sm font-medium transition-colors',
                      category === item ? 'border-primary bg-primary text-white' : 'border-gray-300 bg-white text-gray-700 hover:border-primary')}>
                    {item}
                  </button>
                ))}
              </div>
              <label className="flex flex-col gap-1 text-sm font-medium text-gray-700">
                Search products
                <input type="search" value={query} onChange={(event) => setQuery(event.target.value)}
                  placeholder="Name or description" className="form-input min-w-60" />
              </label>
            </div>
            <p className="mb-4 text-sm text-gray-500" aria-live="polite">Showing {visibleProducts.length} of {products.length} demo entries</p>
            {visibleProducts.length === 0 ? <Empty>No entries match these filters.</Empty> :
              <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
                {visibleProducts.map((p) => (
                  <li key={p.id} className="flex flex-col overflow-hidden rounded-2xl bg-white shadow-card">
                    <img src={p.imagePath} alt={p.imageAlt} loading="lazy"
                      className={cn('aspect-[4/3] w-full bg-gray-100',
                        brand.slug === 'streambox' ? 'object-cover' : 'object-contain')}
                      onError={(event) => {
                        if (!event.currentTarget.src.endsWith('/catalog/fallback.svg')) event.currentTarget.src = '/catalog/fallback.svg';
                      }} />
                    <div className="flex flex-1 flex-col p-5">
                      <div className="text-xs font-semibold uppercase tracking-wide text-gray-500">{p.category}</div>
                      <h2 className="mt-2 text-lg font-semibold text-gray-800">{p.name}</h2>
                      <p className="mt-2 flex-1 text-sm leading-relaxed text-gray-600">{p.description}</p>
                      <div className="mt-4 text-2xl font-bold text-gray-800">{formatCurrency(p.price)}</div>
                      <div className="text-xs text-gray-500">Illustrative demo price</div>
                      <Link to={`/apply?partner=${brand.slug}&product=${p.id}`}
                        className={cn('mt-4 block rounded-lg px-4 py-2.5 text-center text-sm font-semibold transition-smooth', brand.button)}>
                        Apply with Rocket Credit
                      </Link>
                    </div>
                  </li>
                ))}
              </ul>}
          </>}
          <p className="mt-8 text-sm text-gray-500"><a href="/catalog/credits.html" className="underline hover:text-primary">Image sources and licenses</a></p>
        </div>
      </section>
    </div>
  );
};

export default StorePage;

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
          <h1 className="mt-4 text-4xl font-bold sm:text-5xl">
            <span className="mr-3" aria-hidden="true">{brand.emoji}</span>{brand.name}
          </h1>
          <p className="mt-3 max-w-2xl text-lg opacity-90">{brand.tagline}</p>
          <p className="mt-6 inline-flex items-center gap-2 rounded-full bg-black/10 px-3 py-1 text-sm">
            Financing by <strong>Rocket Credit</strong>
            {partner.data && <span>· up to {formatCurrency(partner.data.amountCap)} per request</span>}
          </p>
        </div>
      </section>

      <section className="bg-secondary-50 py-12">
        <div className="mx-auto max-w-6xl px-4 sm:px-6 lg:px-8">
          <p className="mb-8 max-w-3xl text-gray-600">
            {brand.blurb} This is a demonstration store inside the Rocket Credit app: there is no cart, checkout or
            payment. “Apply with Rocket Credit” opens a financing request for the product{user ? '' : ' after you sign in'}.
          </p>

          {partner.status === 'loading' && <Loading label="Loading catalog…" />}
          {partner.status === 'error' && <Notice tone="error">{errorMessage(partner.error)}</Notice>}
          {partner.status === 'ready' && partner.data.products.length === 0 && <Empty>No products in this catalog.</Empty>}
          {partner.status === 'ready' && (
            <ul className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
              {partner.data.products.map((p) => (
                <li key={p.id} className="flex flex-col rounded-2xl bg-white p-6 shadow-card">
                  <div className={cn('mb-4 flex h-28 items-center justify-center rounded-xl text-5xl', brand.accent)} aria-hidden="true">
                    {brand.emoji}
                  </div>
                  <h2 className="text-lg font-semibold text-gray-800">{p.name}</h2>
                  <div className="mt-1 text-2xl font-bold text-gray-800">{formatCurrency(p.price)}</div>
                  <div className="mt-auto pt-4">
                    <Link
                      to={`/apply?partner=${brand.slug}&product=${p.id}`}
                      className={cn('block rounded-lg px-4 py-2.5 text-center text-sm font-semibold transition-smooth', brand.button)}
                    >
                      Apply with Rocket Credit
                    </Link>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </div>
      </section>
    </div>
  );
};

export default StorePage;

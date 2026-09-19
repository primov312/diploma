import { Link } from 'react-router-dom';
import { partnersApi } from '../../api/rocket';
import { Loading, Notice, Page } from '../../components/app/Ui';
import { errorMessage, useApi } from '../../hooks/useApi';
import { cn } from '../../utils/cn';
import { formatCurrency } from '../../utils/format';
import { BRANDS } from './brands';

const StoresIndexPage = () => {
  const partners = useApi(() => partnersApi.list(), []);
  return (
    <Page title="Demo stores" subtitle="Three partner stores inside this app. Each product can be financed through Rocket Credit.">
      {partners.status === 'loading' && <Loading />}
      {partners.status === 'error' && <Notice tone="error">{errorMessage(partners.error)}</Notice>}
      {partners.status === 'ready' && (
        <ul className="grid gap-6 md:grid-cols-3">
          {partners.data.map((p) => {
            const brand = BRANDS[p.slug];
            return (
              <li key={p.slug}>
                <Link to={`/stores/${p.slug}`} className={cn('block rounded-2xl p-6 shadow-card transition-smooth hover:shadow-hover', brand?.accent ?? 'bg-white', brand?.text ?? 'text-gray-800')}>
                  <div className="text-4xl" aria-hidden="true">{brand?.emoji}</div>
                  <h2 className="mt-3 text-2xl font-bold">{p.displayName}</h2>
                  <p className="mt-1 opacity-90">{brand?.tagline}</p>
                  <p className="mt-4 text-sm opacity-80">Financing up to {formatCurrency(p.amountCap)}</p>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
    </Page>
  );
};

export default StoresIndexPage;

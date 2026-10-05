import { Link } from 'react-router-dom';
import { partnersApi } from '../../api/rocket';
import { Loading, Notice, Page, SyntheticTag } from '../../components/app/Ui';
import { errorMessage, useApi } from '../../hooks/useApi';
import { cn } from '../../utils/cn';
import { formatCurrency } from '../../utils/format';
import { Icon } from '../../components/common/Icon';
import { BRANDS } from './brands';

const StoresIndexPage = () => {
  const partners = useApi(() => partnersApi.list(), []);
  return (
    <Page title="Demo stores" subtitle="Browse illustrative products and start a financing request.">
      {partners.status === 'loading' && <Loading />}
      {partners.status === 'error' && <Notice tone="error">{errorMessage(partners.error)}</Notice>}
      {partners.status === 'ready' && (
        <ul className="grid gap-6 md:grid-cols-3">
          {partners.data.map((p) => {
            const brand = BRANDS[p.slug];
            return (
              <li key={p.slug}>
                <Link to={`/stores/${p.slug}`} className={cn('block rounded-lg border border-gray-200 p-6 transition-colors hover:border-accent', brand?.accent ?? 'bg-white', brand?.text ?? 'text-gray-800')}>
                  <div className="text-2xl font-black tracking-tight" aria-hidden="true">{brand?.wordmark}</div>
                  <h2 className="mt-3 text-2xl font-bold">{p.displayName}</h2>
                  <p className="mt-1 text-primary-100">{brand?.category}</p>
                  <p className="mt-4 text-sm text-primary-100">Financing up to {formatCurrency(p.amountCap)}</p>
                  <span className="mt-6 flex items-center gap-2 font-semibold">View catalog<Icon name="arrow" /></span>
                </Link>
              </li>
            );
          })}
        </ul>
      )}
      <div className="mt-6"><SyntheticTag>Demo catalogs</SyntheticTag><details className="mt-4"><summary>About these catalogs</summary><p>Products and prices are illustrative. Brand names do not imply affiliation. No checkout or payment is provided.</p></details></div>
    </Page>
  );
};

export default StoresIndexPage;

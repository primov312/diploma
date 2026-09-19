import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { applicationsApi, partnersApi } from '../../api/rocket';
import type { PartnerDetail } from '../../api/types';
import { Card, Loading, Notice, Page } from '../../components/app/Ui';
import { errorMessage, useApi } from '../../hooks/useApi';
import { formatCurrency } from '../../utils/format';

/**
 * One form for both entry points:
 *   /apply                                  direct: choose a store and an amount
 *   /apply?partner=threadly&product=14      from a store page: the product is prefilled
 * The backend resolves the product price itself; the amount shown here is informational.
 */
const ApplyPage = () => {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const partners = useApi(() => partnersApi.list(), []);

  const [partnerSlug, setPartnerSlug] = useState(params.get('partner') ?? '');
  const [productId, setProductId] = useState<number | null>(params.get('product') ? Number(params.get('product')) : null);
  const [amount, setAmount] = useState(params.get('amount') ?? '');
  const [useAi, setUseAi] = useState(false);
  const [detail, setDetail] = useState<PartnerDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    if (!partnerSlug) {
      setDetail(null);
      return;
    }
    let cancelled = false;
    partnersApi.get(partnerSlug).then((d) => !cancelled && setDetail(d)).catch(() => !cancelled && setDetail(null));
    return () => {
      cancelled = true;
    };
  }, [partnerSlug]);

  useEffect(() => {
    if (partners.status === 'ready' && !partnerSlug && partners.data.length > 0) setPartnerSlug(partners.data[0].slug);
  }, [partners.status, partners.data, partnerSlug]);

  const product = useMemo(() => detail?.products.find((p) => p.id === productId) ?? null, [detail, productId]);
  const cap = detail?.amountCap;

  const onSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (submitting) return; // no double submit while a request is pending
    setError(null);
    setFieldErrors({});
    const parsed = product ? product.price : Number(amount);
    if (!product && (!amount || !Number.isFinite(parsed) || parsed <= 0)) {
      setFieldErrors({ amount: 'Enter an amount greater than zero.' });
      return;
    }
    setSubmitting(true);
    try {
      const saved = await applicationsApi.submit(
        product ? { partnerSlug, productId: product.id, useAi } : { partnerSlug, requestedAmount: Math.round(parsed * 100) / 100, useAi },
      );
      navigate(`/applications/${saved.id}`, { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.code === 'ANALYSIS_UNAVAILABLE') {
        setError('The analysis service is unavailable right now. Nothing was saved — please try again in a moment.');
      } else if (err instanceof ApiError && err.code === 'VALIDATION_FAILED') {
        setFieldErrors(err.fields);
        setError('Please correct the highlighted fields.');
      } else if (err instanceof ApiError) {
        setError(err.message);
      } else {
        setError('Something went wrong. Nothing was saved.');
      }
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Page title="Apply for financing" subtitle="Choose a store and an amount. The decision is explained and saved to your account; no money is moved.">
      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          {partners.status === 'loading' && <Loading />}
          {partners.status === 'error' && <Notice tone="error">{errorMessage(partners.error)}</Notice>}
          {partners.status === 'ready' && (
            <form className="space-y-5" onSubmit={onSubmit} noValidate>
              <div>
                <label htmlFor="store" className="block text-sm font-medium text-gray-700">Store</label>
                <select
                  id="store"
                  className="form-input mt-1 w-full py-2"
                  value={partnerSlug}
                  onChange={(e) => {
                    setPartnerSlug(e.target.value);
                    setProductId(null);
                  }}
                >
                  {partners.data.map((p) => (
                    <option key={p.slug} value={p.slug}>{p.displayName} — up to {formatCurrency(p.amountCap)}</option>
                  ))}
                </select>
              </div>

              <div>
                <label htmlFor="product" className="block text-sm font-medium text-gray-700">Product (optional)</label>
                <select
                  id="product"
                  className="form-input mt-1 w-full py-2"
                  value={productId ?? ''}
                  onChange={(e) => setProductId(e.target.value ? Number(e.target.value) : null)}
                >
                  <option value="">No product — enter an amount</option>
                  {detail?.products.map((p) => (
                    <option key={p.id} value={p.id}>{p.name} — {formatCurrency(p.price)}</option>
                  ))}
                </select>
              </div>

              <div>
                <label htmlFor="amount" className="block text-sm font-medium text-gray-700">Amount (USD)</label>
                <input
                  id="amount"
                  type="number"
                  inputMode="decimal"
                  min="0.01"
                  step="0.01"
                  className="form-input mt-1 w-full py-2 disabled:bg-gray-50"
                  value={product ? product.price : amount}
                  disabled={Boolean(product)}
                  onChange={(e) => setAmount(e.target.value)}
                  aria-invalid={Boolean(fieldErrors.amount || fieldErrors.requestedAmount)}
                  aria-describedby="amount-hint"
                />
                <p id="amount-hint" className="mt-1 text-xs text-gray-500">
                  {product ? 'The price comes from the store catalog and cannot be changed here.' : cap ? `This store finances up to ${formatCurrency(cap)}.` : ''}
                </p>
                {(fieldErrors.amount || fieldErrors.requestedAmount) && (
                  <p className="mt-1 text-xs text-error-700">{fieldErrors.amount ?? fieldErrors.requestedAmount}</p>
                )}
              </div>

              <label className="flex items-start gap-3 rounded-lg border border-gray-200 p-3">
                <input type="checkbox" className="mt-1" checked={useAi} onChange={(e) => setUseAi(e.target.checked)} />
                <span className="text-sm">
                  <span className="font-medium text-gray-800">Use AI analysis for this request</span>
                  <br />
                  <span className="text-gray-600">Adds a trained model’s risk estimate to the rules. If no model is available, the rules alone decide and the result says so.</span>
                </span>
              </label>

              {error && <Notice tone="error">{error}</Notice>}

              <button type="submit" className="btn-gradient w-full py-3 disabled:opacity-60" disabled={submitting} aria-busy={submitting}>
                {submitting ? 'Checking…' : 'Submit request'}
              </button>
            </form>
          )}
        </Card>

        <Card title="What happens" className="text-sm text-gray-600">
          <ol className="list-decimal space-y-2 pl-5">
            <li>Your request is checked against the store’s limit.</li>
            <li>Your profile, purchase history with this store and synthetic financial profile are turned into features.</li>
            <li>The analysis service applies transparent rules (and the AI model if enabled) and explains the result.</li>
            <li>The request, the features and the decision are saved together. You can revisit it any time.</li>
          </ol>
          <p className="mt-4 text-xs text-gray-500">Outcomes are approved, not approved or needs review. “Needs review” means the automatic check was inconclusive. This is a demonstration: no payment or transfer happens.</p>
        </Card>
      </div>
    </Page>
  );
};

export default ApplyPage;

import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { applicationsApi, partnersApi } from '../../api/rocket';
import type { PartnerDetail } from '../../api/types';
import { Card, Loading, Notice, Page, SyntheticTag } from '../../components/app/Ui';
import { errorMessage, useApi } from '../../hooks/useApi';
import { formatCurrency, hufToUsd, usdToHuf } from '../../utils/format';
import LocalDropdown from '../../components/common/LocalDropdown';

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
    const parsed = product ? product.price : hufToUsd(Number(amount));
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
    <Page title="Apply for financing" subtitle="Choose a store and amount. Review your saved demo decision after submitting.">
      <div className="grid gap-6 lg:grid-cols-3">
        <Card title="Your request" icon="file" className="lg:col-span-2">
          {partners.status === 'loading' && <Loading />}
          {partners.status === 'error' && <Notice tone="error">{errorMessage(partners.error)}</Notice>}
          {partners.status === 'ready' && (
            <form className="space-y-5" onSubmit={onSubmit} noValidate>
              <div>
                <label htmlFor="store" className="block text-sm font-medium text-gray-700">Store</label>
                <LocalDropdown
                  id="store"
                  className="mt-1 w-full"
                  value={partnerSlug}
                  onValueChange={(value) => {
                    setPartnerSlug(value);
                    setProductId(null);
                  }}
                  options={partners.data.map((p) => ({ value: p.slug, label: `${p.displayName} — up to ${formatCurrency(p.amountCap)}` }))}
                />
              </div>

              <div>
                <label htmlFor="product" className="block text-sm font-medium text-gray-700">Product (optional)</label>
                <LocalDropdown
                  id="product"
                  className="mt-1 w-full"
                  value={productId == null ? '' : String(productId)}
                  onValueChange={(value) => setProductId(value ? Number(value) : null)}
                  placeholder="No product — enter an amount"
                  options={detail?.products.map((p) => ({ value: String(p.id), label: `${p.name} — ${formatCurrency(p.price)}` })) ?? []}
                />
              </div>

              <div>
                <label htmlFor="amount" className="block text-sm font-medium text-gray-700">Amount (HUF)</label>
                <input
                  id="amount"
                  type="number"
                  inputMode="decimal"
                  min="1"
                  step="1"
                  className="form-input mt-1 w-full py-2 disabled:bg-gray-50"
                  value={product ? usdToHuf(product.price) : amount}
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
                  <span className="text-gray-600">Adds a model risk estimate when available. Otherwise the decision uses rules.</span>
                </span>
              </label>

              {error && <Notice tone="error">{error}</Notice>}

              <button type="submit" className="btn-primary w-full py-3 disabled:opacity-60" disabled={submitting} aria-busy={submitting}>
                {submitting ? 'Checking…' : 'Submit request'}
              </button>
            </form>
          )}
        </Card>

        <Card icon="info" title="What happens" className="text-sm text-gray-600">
          <SyntheticTag>Demo application</SyntheticTag>
          <ol className="mt-4 list-decimal space-y-3 pl-5"><li>Check the store limit and your account information.</li><li>Review the decision and its reasons.</li><li>Reopen the saved request in Applications.</li></ol>
          <p className="mt-4">No real lending or payments. Outcomes are approved, not approved or needs review.</p>
          <details className="mt-5"><summary>How the request is assessed</summary><p>Your profile, purchase history and synthetic financial information are evaluated by rules and the AI model when selected and available. The request and decision are saved together. Needs review indicates an inconclusive automatic check.</p></details>
        </Card>
      </div>
    </Page>
  );
};

export default ApplyPage;

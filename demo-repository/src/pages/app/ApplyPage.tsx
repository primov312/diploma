import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { applicationsApi, catalogApi, partnersApi } from '../../api/rocket';
import { paymentsApi, type CreditQuote, type TermUnit } from '../../api/payments';
import type { CatalogItem } from '../../api/types';
import { Card, Loading, Notice, Page } from '../../components/app/Ui';
import { ScheduleReview } from '../../components/payments/PaymentUi';
import LocalDropdown from '../../components/common/LocalDropdown';
import { Icon } from '../../components/common/Icon';
import { errorMessage, useApi } from '../../hooks/useApi';
import { usePaymentAction } from '../../hooks/useFinancial';
import { formatCurrency, hufToUsd } from '../../utils/format';

const STEPS = ['What you buy', 'Repayment', 'Review'];

const ProductSummary = ({ item, onChange }: { item: CatalogItem; onChange?: () => void }) =>
  <div className="flex items-center gap-4 rounded-md border border-gray-200 p-3">
    <img src={item.imagePath} alt="" className="h-16 w-16 shrink-0 rounded bg-gray-100 object-contain" />
    <div className="min-w-0 flex-1"><p className="truncate font-semibold text-gray-800">{item.name}</p><p className="text-sm text-gray-500">{item.partnerName} · {formatCurrency(item.price)}</p></div>
    {onChange && <button type="button" className="text-sm underline" onClick={onChange}>Change</button>}
  </div>;

export default function ApplyPage() {
  const [params] = useSearchParams(); const navigate = useNavigate();
  const catalog = useApi(() => catalogApi.list(), []);
  const partners = useApi(() => partnersApi.list(), []);
  const [productId, setProductId] = useState<number | null>(params.get('product') ? Number(params.get('product')) : null);
  const [mode, setMode] = useState<'product' | 'amount'>(params.get('amount') && !params.get('product') ? 'amount' : 'product');
  const [seller, setSeller] = useState(params.get('partner') ?? '');
  const [amount, setAmount] = useState(params.get('amount') ?? '');
  const [search, setSearch] = useState('');
  const [useAi, setUseAi] = useState(false);
  const [step, setStep] = useState(1), [unit, setUnit] = useState<TermUnit>('MONTH'), [count, setCount] = useState('');
  const [quote, setQuote] = useState<CreditQuote>(), [error, setError] = useState<string>(), [quoting, setQuoting] = useState(false);
  const action = usePaymentAction();

  const items = catalog.status === 'ready' ? catalog.data : [];
  const product = mode === 'product' ? items.find(item => item.id === productId) : undefined;
  const sellers = partners.status === 'ready' ? partners.data : [];
  useEffect(() => { if (!seller && sellers.length) setSeller(sellers[0].slug); }, [sellers.length]);
  const sellerCap = sellers.find(p => p.slug === seller)?.amountCap;
  const matches = useMemo(() => {
    const terms = search.trim().toLowerCase();
    return terms ? items.filter(item => `${item.name} ${item.partnerName} ${item.category}`.toLowerCase().includes(terms)).slice(0, 6) : [];
  }, [items, search]);

  const request = product ? { partnerSlug: product.partnerSlug, productId: product.id } : { partnerSlug: seller, requestedAmount: hufToUsd(Number(amount)) };

  const continueFromPurchase = () => {
    setError(undefined);
    if (mode === 'product' && !product) return setError('Choose a product, or switch to “Another amount”.');
    if (mode === 'amount' && !(Number(request.requestedAmount) > 0)) return setError('Enter an amount greater than zero.');
    setStep(2);
  };
  const preview = async () => {
    setError(undefined); setQuoting(true);
    try { setQuote(await paymentsApi.quote({ ...request, termUnit: unit, installmentCount: Number(count) })); setStep(3); }
    catch (e) { setError(e instanceof Error ? e.message : 'Unable to prepare terms.'); }
    finally { setQuoting(false); }
  };
  const submit = async () => {
    if (!quote) return;
    const body = { ...request, quoteId: quote.id, useAi };
    const saved = await action.run(JSON.stringify(body), key => applicationsApi.submit(body, key));
    if (saved) navigate(`/applications/${saved.id}`, { replace: true });
  };

  return <Page title="Apply for credit">
    <ol aria-label="Application steps" className="mb-6 grid grid-cols-3 gap-2 text-sm">{STEPS.map((label, i) =>
      <li key={label} aria-current={step === i + 1 ? 'step' : undefined} className={`border-b-2 py-3 ${step === i + 1 ? 'border-primary font-semibold text-primary' : step > i + 1 ? 'border-primary-300 text-gray-700' : 'border-gray-200 text-gray-500'}`}>{i + 1}. {label}</li>)}</ol>
    <div className="grid gap-6 lg:grid-cols-[2fr_1fr]">
      <Card>
        {(catalog.status === 'loading' || partners.status === 'loading') && <Loading />}
        {catalog.status === 'error' && <Notice tone="error">{errorMessage(catalog.error)}</Notice>}

        {step === 1 && catalog.status === 'ready' && <form noValidate className="space-y-5" onSubmit={e => { e.preventDefault(); continueFromPurchase(); }}>
          <div className="inline-flex rounded-md border border-gray-200 p-1" role="group" aria-label="What are you buying">
            {([['product', 'A marketplace product'], ['amount', 'Another amount']] as const).map(([value, label]) =>
              <button key={value} type="button" aria-pressed={mode === value} onClick={() => { setMode(value); setError(undefined); }}
                className={`min-h-11 rounded px-4 text-sm font-medium ${mode === value ? 'bg-primary text-white' : 'text-gray-700'}`}>{label}</button>)}
          </div>
          {mode === 'product' && (product
            ? <ProductSummary item={product} onChange={() => setProductId(null)} />
            : <div>
              <label className="block text-sm font-medium">Find a product
                <span className="relative mt-1 block"><Icon name="search" className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-500" />
                  <input type="search" autoFocus value={search} onChange={e => setSearch(e.target.value)} placeholder="e.g. coat, headphones, series" className="form-input w-full pl-10" /></span>
              </label>
              {matches.length > 0 && <ul className="mt-2 divide-y divide-gray-100 rounded-md border border-gray-200">{matches.map(item =>
                <li key={item.id}><button type="button" className="flex min-h-11 w-full items-center justify-between gap-3 px-3 py-2 text-left text-sm hover:bg-gray-50" onClick={() => { setProductId(item.id); setSearch(''); }}>
                  <span><span className="font-medium">{item.name}</span><span className="block text-xs text-gray-500">{item.partnerName}</span></span><span className="shrink-0 font-semibold">{formatCurrency(item.price)}</span>
                </button></li>)}</ul>}
              {search.trim() && matches.length === 0 && <p className="mt-2 text-sm text-gray-500">No match. Try another word.</p>}
              <p className="mt-2 text-sm text-gray-500">Or browse the <Link to="/marketplace" className="underline">marketplace</Link> and press “Apply for credit”.</p>
            </div>)}
          {mode === 'amount' && <div className="grid gap-4 sm:grid-cols-2">
            <label className="block text-sm font-medium">Amount (HUF)<input type="number" inputMode="decimal" min="2" step="1" required value={amount} onChange={e => setAmount(e.target.value)} className="form-input mt-1 w-full" /></label>
            <label className="block text-sm font-medium">Seller<LocalDropdown id="store" aria-label="Seller" className="mt-1 w-full" value={seller} onValueChange={setSeller} options={sellers.map(p => ({ value: p.slug, label: p.displayName }))} /></label>
            {sellerCap != null && <p className="text-sm text-gray-500 sm:col-span-2">Up to {formatCurrency(sellerCap)} with this seller.</p>}
          </div>}
          <button className="btn-primary">Continue</button>
        </form>}

        {step === 2 && <form className="space-y-5" onSubmit={e => { e.preventDefault(); void preview(); }}>
          {product && <ProductSummary item={product} />}
          <fieldset><legend className="mb-2 text-sm font-medium">Pay back</legend><div className="flex gap-3">{(['MONTH', 'WEEK'] as const).map(value =>
            <label key={value} className={`flex min-h-11 cursor-pointer items-center gap-2 rounded-md border px-4 ${unit === value ? 'border-primary bg-primary-50' : 'border-gray-200'}`}><input type="radio" name="term-unit" value={value} checked={unit === value} onChange={() => { setUnit(value); if (value === 'WEEK' && Number(count) > 3) setCount(''); }} />{value === 'WEEK' ? 'Weekly' : 'Monthly'}</label>)}</div></fieldset>
          <label className="block text-sm font-medium">Number of payments<select required className="form-input mt-1 w-full" value={count} onChange={e => setCount(e.target.value)}><option value="">Choose</option>{Array.from({ length: unit === 'WEEK' ? 3 : 24 }, (_, i) => <option key={i + 1} value={i + 1}>{i + 1} {unit === 'WEEK' ? 'week' : 'month'}{i ? 's' : ''}</option>)}</select></label>
          <div className="flex gap-3"><button type="button" className="btn-secondary" onClick={() => setStep(1)}>Back</button><button className="btn-primary disabled:opacity-50" disabled={quoting || !count}>{quoting ? 'Calculating…' : 'See my payments'}</button></div>
        </form>}

        {step === 3 && quote && <div className="space-y-5">
          <ScheduleReview schedule={quote.schedule} />
          <label className="flex cursor-pointer gap-3 rounded-md border border-gray-200 p-3 text-sm"><input type="checkbox" checked={useAi} onChange={e => setUseAi(e.target.checked)} /><span><strong>Also use AI analysis</strong><span className="block text-gray-600">A trained model adds a risk estimate to the rules.</span></span></label>
          <div className="flex flex-wrap gap-3"><button className="btn-secondary" disabled={action.pending} onClick={() => { setQuote(undefined); setStep(2); }}>Back</button><button className="btn-primary disabled:opacity-50" disabled={action.pending} onClick={submit}>{action.pending ? 'Checking…' : 'Get my decision'}</button></div>
          <p className="text-xs text-gray-500">Quote valid until {new Date(quote.expiresAt).toLocaleTimeString()}.</p>
        </div>}
        {(error || action.error) && <div className="mt-4"><Notice tone="error">{error ?? action.error}</Notice></div>}
      </Card>
      <Card title="How it works" icon="info"><ol className="list-decimal space-y-3 pl-5 text-sm text-gray-600">
        <li>Choose what you buy and how to pay it back.</li>
        <li>You get an instant decision with reasons.</li>
        <li>If approved, accept to pay the seller. Nothing happens until you accept.</li>
      </ol></Card>
    </div>
  </Page>;
}

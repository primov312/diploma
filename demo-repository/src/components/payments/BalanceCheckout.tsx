import { Link } from 'react-router-dom';
import { useState } from 'react';
import type { Product } from '../../api/types';
import { paymentsApi } from '../../api/payments';
import { useFinancial, usePaymentAction } from '../../hooks/useFinancial';
import { Card, Loading, Notice } from '../app/Ui';
import { exactMoney } from './PaymentUi';
export default function BalanceCheckout({ product, partner, close }: { product: Pick<Product, 'id' | 'name' | 'price'>; partner: string; close: () => void }) {
  const wallet = useFinancial(paymentsApi.wallet); const action = usePaymentAction(); const [receipt, setReceipt] = useState<number>();
  const returnTo = `/marketplace?pay=${product.id}`;
  return <Card title={`Pay for ${product.name}`} icon="wallet">
    {receipt ? <div className="space-y-3"><Notice tone="info">Purchase #{receipt} completed. Your balance and history have been updated.</Notice><div className="flex flex-wrap gap-3"><Link className="btn-secondary" to="/history">View activity</Link><button className="btn-primary" onClick={close}>Continue shopping</button></div></div> : <div className="space-y-4">
      {wallet.loading && <Loading />}{wallet.error && <Notice tone="error">{wallet.error.message}</Notice>}
      <p className="font-semibold">Purchase total: {exactMoney(product.price)}</p>
      {wallet.data && <><p className="text-sm">Current balance: {exactMoney(wallet.data.balance)}</p>{Number(wallet.data.balance) >= product.price ? <p className="text-sm">Balance after payment: {exactMoney(Number(wallet.data.balance) - product.price)}</p> : <Notice tone="warning">Add {exactMoney(product.price - Number(wallet.data.balance))} to complete this purchase. <Link className="underline" to={`/balance?returnTo=${encodeURIComponent(returnTo)}#top-up`}>Add demo money</Link></Notice>}</>}
      {action.error && <Notice tone="error">{action.error}</Notice>}
      <div className="flex flex-wrap gap-3"><button className="btn-primary disabled:opacity-50" disabled={action.pending || !wallet.data || Number(wallet.data.balance) < product.price} onClick={async () => { const result = await action.run(`purchase:${partner}:${product.id}:${product.price}`, key => paymentsApi.purchase(partner, product.id, product.price.toFixed(2), key)); if (result?.purchaseId) setReceipt(result.purchaseId); }}>{action.pending ? 'Paying…' : 'Confirm payment from balance'}</button><button disabled={action.pending} className="btn-secondary" onClick={close}>Cancel</button></div>
    </div>}
  </Card>;
}

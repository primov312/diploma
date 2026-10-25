import { api } from './client';
export type Money = string;
export type TermUnit = 'WEEK' | 'MONTH';
export type SchedulePart = { sequence: number; periodStart: string; dueDate: string; principal: Money; interest: Money; total: Money };
export type Schedule = { principal: Money; annualRate: Money; termUnit: TermUnit; installmentCount: number; pricingVersion: string; startDate: string; totalInterest: Money; totalRepayment: Money; installments: SchedulePart[] };
export type CreditQuote = { id: number; partnerId: number; productId: number | null; generation: number; expiresAt: string; schedule: Schedule };
export type QuoteRequest = { partnerSlug: string; productId?: number; requestedAmount?: number; termUnit: TermUnit; installmentCount: number };
export type Wallet = { balance: Money; currency: string; version: number; createdAt: string; outstanding: Money; overdue: Money; nextDueDate: string | null; nextPayment: Money | null; monthlyObligations: Money };
export type Activity = { id: string; date: string; kind: string; description: string; amount: Money; balanceChange: Money; status: string; paymentMethod: string; paidOnTime: boolean | null; partnerSlug: string | null; partnerName: string | null; loanId: number | null; applicationId: number | null };
export type PageResult<T> = { items: T[]; page: number; size: number; total: number };
export type Installment = { id: number; loanId: number; applicationId: number; sequence: number; dueDate: string; principal: Money; interest: Money; total: Money; principalPaid: Money; interestPaid: Money; interestWaived: Money; status: string; canPay: boolean; partnerName: string; productName: string | null; paidAt: string | null };
export type Loan = { id: number; applicationId: number; principal: Money; annualRate: Money; termUnit: TermUnit; installmentCount: number; status: string; startDate: string; partnerName: string; productName: string | null; installments: Installment[] };
export type SettlementQuote = { id: number; loanId: number; principal: Money; interest: Money; waivedInterest: Money; total: Money; expiresAt: string };
export type PaymentResult = { balance: Money; currency: string; loanId?: number; purchaseId?: number; entryId?: number };
export type WalletChart = { openingBalance: Money; closingBalance?: Money; points: { date: string; balance: Money }[]; flows: { date: string; topUps: Money; purchases: Money; repayments: Money }[] };
export const queryString = (filters: Record<string, string | number | undefined>) => {
  const params = new URLSearchParams();
  Object.entries(filters).forEach(([key, value]) => { if (value !== undefined && value !== '') params.set(key, String(value)); });
  return params.toString();
};
const post = <T>(path: string, body: unknown, key?: string) => api<T>(path, { method: 'POST', body, idempotencyKey: key });
export const paymentsApi = {
  wallet: () => api<Wallet>('/api/wallet'),
  chart: (range: string) => api<WalletChart>(`/api/wallet/chart?range=${range}`),
  topUp: (amount: string, key: string) => post<PaymentResult>('/api/wallet/top-ups', { amount }, key),
  purchase: (partnerSlug: string, productId: number, expectedAmount: string, key: string) => post<PaymentResult>('/api/purchases', { partnerSlug, productId, expectedAmount }, key),
  quote: (body: QuoteRequest) => post<CreditQuote>('/api/credit-quotes', body),
  accept: (id: number, key: string) => post<PaymentResult>(`/api/applications/${id}/accept`, undefined, key),
  history: (filters: Record<string, string | number | undefined> = {}) => api<PageResult<Activity>>(`/api/history?${queryString(filters)}`),
  repayments: (filters: Record<string, string | number | undefined> = {}) => api<PageResult<Installment>>(`/api/repayments?${queryString(filters)}`),
  loan: (id: number) => api<Loan>(`/api/loans/${id}`),
  pay: (id: number, key: string) => post<PaymentResult>(`/api/repayments/${id}/pay`, undefined, key),
  settlementQuote: (id: number) => post<SettlementQuote>(`/api/loans/${id}/settlement-quotes`, undefined),
  settle: (id: number, quoteId: number, key: string) => post<PaymentResult>(`/api/loans/${id}/settle`, { quoteId }, key),
};

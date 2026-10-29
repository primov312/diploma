// AI-assisted (Claude), 2026; reviewed by the author.
import { api } from './client';
import type { Role } from './auth';

/** Seller and administrator API. Amounts are USD numbers, as the backend stores them. */

export type Page<T> = { items: T[]; page: number; size: number; total: number };

// ---- seller -------------------------------------------------------------------------------------

export type SellerPartner = { slug: string; name: string; amountCap: number; active: boolean };
export type DayPoint = { day: string; orders: number; principal: number };
export type SellerOverview = {
  partner: SellerPartner;
  applicationsByDecision: Record<string, number>;
  financedOrders: number;
  financedPrincipal: number;
  activeLoans: number;
  closedLoans: number;
  last30Days: DayPoint[];
};
export type LoanStatus = 'ACTIVE' | 'PAID' | 'SETTLED_EARLY';
export type SellerOrder = {
  orderRef: number;
  customerRef: string;
  productName: string | null;
  principal: number;
  termUnit: 'WEEK' | 'MONTH';
  installmentCount: number;
  loanStatus: LoanStatus;
  createdAt: string;
};
export type SellerProduct = {
  id: number; name: string; description: string; category: string;
  imagePath: string; imageAlt: string; price: number; active: boolean;
};
export type SellerCatalogue = {
  partner: SellerPartner;
  categories: string[];
  images: { path: string; alt: string }[];
  items: SellerProduct[];
};
export type ProductForm = { name: string; description: string; category: string; imagePath: string; price: number };

const query = (params: Record<string, string | number | undefined>) => {
  const q = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => { if (value !== undefined && value !== '') q.set(key, String(value)); });
  const text = q.toString();
  return text ? `?${text}` : '';
};

export const sellerApi = {
  overview: () => api<SellerOverview>('/api/seller/overview'),
  orders: (params: { status?: string; from?: string; to?: string; page?: number; size?: number }) =>
    api<Page<SellerOrder>>(`/api/seller/orders${query(params)}`),
  catalogue: () => api<SellerCatalogue>('/api/seller/products'),
  create: (body: ProductForm) => api<SellerProduct>('/api/seller/products', { method: 'POST', body }),
  update: (id: number, body: ProductForm) => api<SellerProduct>(`/api/seller/products/${id}`, { method: 'PUT', body }),
  setActive: (id: number, active: boolean) =>
    api<SellerProduct>(`/api/seller/products/${id}/${active ? 'activate' : 'deactivate'}`, { method: 'POST' }),
};

// ---- administrator ------------------------------------------------------------------------------

export type AdminOverview = {
  usersByRole: Record<Role, { enabled: number; disabled: number }>;
  applicationsByDecision: Record<string, number>;
  reviews: { open: number; approved: number; rejected: number };
  activeLoans: number;
  overdueInstallments: number;
  services: Record<string, string>;
};
export type AdminUser = {
  id: number; email: string; displayName: string; role: Role; enabled: boolean; createdAt: string;
  partnerSlug: string | null; partnerName: string | null;
};
export type ReviewItem = {
  applicationId: number; customerId: number; partnerName: string; productName: string | null;
  requestedAmount: number; possibleAmount: number; score: number; createdAt: string;
  outcome: 'APPROVED' | 'REJECTED' | null; approvedAmount: number | null; decidedAt: string | null;
};
export type ReviewDetail = {
  applicationId: number; customerId: number; partnerName: string; productName: string | null;
  requestedAmount: number; currency: string; decisionStatus: 'REVIEW'; score: number; possibleAmount: number;
  reasons: string[]; factors: Record<string, { score: number | null; weight: number; reasons?: string[] }>;
  aiStatus: string; policyVersion: string; modelVersion: string | null; createdAt: string;
  plan: { termUnit: 'WEEK' | 'MONTH'; installmentCount: number; annualRate: string; totalRepayment: string } | null;
  decision: { outcome: 'APPROVED' | 'REJECTED'; approvedAmount: number | null; note: string; reviewerEmail: string; decidedAt: string } | null;
  accepted: boolean;
};
export type AdminPartner = {
  id: number; slug: string; displayName: string; amountCap: number; active: boolean;
  activeProducts: number; products: number; sellers: number;
};
export type AuditEvent = {
  id: number; actorUserId: number | null; actorEmail: string | null; actorRole: Role; action: string;
  targetType: string; targetId: string | null; details: Record<string, unknown>; createdAt: string;
};

export const adminApi = {
  overview: () => api<AdminOverview>('/api/admin/overview'),
  users: (params: { role?: string; q?: string; page?: number; size?: number }) => api<Page<AdminUser>>(`/api/admin/users${query(params)}`),
  createSeller: (body: { email: string; displayName: string; partnerId: number; password: string }) =>
    api<AdminUser>('/api/admin/users/sellers', { method: 'POST', body }),
  setEnabled: (id: number, enabled: boolean) => api<AdminUser>(`/api/admin/users/${id}/${enabled ? 'enable' : 'disable'}`, { method: 'POST' }),
  resetPassword: (id: number, password: string) => api<void>(`/api/admin/users/${id}/reset-password`, { method: 'POST', body: { password } }),
  reviews: (state: 'OPEN' | 'DECIDED') => api<Page<ReviewItem>>(`/api/admin/reviews${query({ state, size: 100 })}`),
  review: (id: number | string) => api<ReviewDetail>(`/api/admin/reviews/${id}`),
  decide: (id: number, body: { outcome: 'APPROVED' | 'REJECTED'; approvedAmount?: number; note: string }) =>
    api<ReviewDetail>(`/api/admin/reviews/${id}/decision`, { method: 'POST', body }),
  partners: () => api<AdminPartner[]>('/api/admin/partners'),
  updatePartner: (id: number, body: { displayName: string; amountCap: number; active: boolean }) =>
    api<AdminPartner>(`/api/admin/partners/${id}`, { method: 'PUT', body }),
  audit: (params: { actor?: string; action?: string; from?: string; to?: string; page?: number; size?: number }) =>
    api<Page<AuditEvent>>(`/api/admin/audit${query(params)}`),
};

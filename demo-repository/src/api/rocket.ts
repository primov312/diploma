import { api } from './client';
import type { Application, FinancialProfile, Partner, PartnerDetail, SubmitApplication, Transaction } from './types';

export const partnersApi = {
  list: () => api<Partner[]>('/api/partners'),
  get: (slug: string) => api<PartnerDetail>(`/api/partners/${encodeURIComponent(slug)}`),
};

export const meApi = {
  profile: () => api<FinancialProfile>('/api/me/profile'),
};

export const transactionsApi = {
  list: (partnerSlug?: string) =>
    api<Transaction[]>(partnerSlug ? `/api/transactions?partner=${encodeURIComponent(partnerSlug)}` : '/api/transactions'),
};

export const applicationsApi = {
  list: () => api<Application[]>('/api/applications'),
  get: (id: number | string) => api<Application>(`/api/applications/${id}`),
  submit: (body: SubmitApplication) => api<Application>('/api/applications', { method: 'POST', body }),
};

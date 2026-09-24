import { api, apiMultipart } from './client';
import type { Address, AddressSaveRequest, AddressSaveResult, AddressVerification, AffordabilityEstimate, AffordabilityHistoryPoint, AnalysisJob, Application, DemoSignalReport, DemoSignalRun, DemoSignalSettings, DistrictCost, FinancialInputs, FinancialInputsSaveResult, FinancialProfile, LocalCostExtraction, Partner, PartnerDetail, RecalculationAccepted, SaveFinancialInputs, SubmitApplication, Transaction } from './types';

export const partnersApi = {
  list: () => api<Partner[]>('/api/partners'),
  get: (slug: string) => api<PartnerDetail>(`/api/partners/${encodeURIComponent(slug)}`),
};

export const meApi = {
  profile: () => api<FinancialProfile>('/api/me/profile'),
  financialInputs: () => api<FinancialInputs>('/api/me/financial-inputs'),
  saveFinancialInputs: (body: SaveFinancialInputs) =>
    api<FinancialInputsSaveResult>('/api/me/financial-inputs', { method: 'PUT', body }),
  affordability: () => api<AffordabilityEstimate>('/api/me/affordability'),
  affordabilityHistory: (months = 12) =>
    api<AffordabilityHistoryPoint[]>(`/api/me/affordability/history?months=${months}`),
  recalculate: () => api<RecalculationAccepted>('/api/me/affordability/recalculate', { method: 'POST' }),
  analysisJob: (id: number) => api<AnalysisJob>(`/api/me/analysis-jobs/${id}`),
  address: () => api<Address>('/api/me/address'),
  saveAddress: (body: AddressSaveRequest) => api<AddressSaveResult>('/api/me/address', { method: 'PUT', body }),
  districts: () => api<DistrictCost[]>('/api/local-costs'),
  researchLocalCosts: (districtId: string) =>
    api<LocalCostExtraction>(`/api/me/local-costs/research?districtId=${encodeURIComponent(districtId)}`, { method: 'POST' }),
  uploadAddressEvidence: (addressRevision: number, file: File) =>
    apiMultipart<AddressVerification>('/api/me/address/verifications', file, { addressRevision: String(addressRevision) }),
  demoSignalSettings: () => api<DemoSignalSettings>('/api/me/demo-signals/settings'),
  updateDemoSignalSettings: (body: Pick<DemoSignalSettings, 'locationEnabled' | 'socialEnabled'>) =>
    api<DemoSignalSettings>('/api/me/demo-signals/settings', { method: 'PUT', body }),
  runLocation: (scenarioId: string) => api<DemoSignalRun>('/api/me/demo-signals/location-runs', { method: 'POST', body: { scenarioId } }),
  runSocial: (scenarioId: string) => api<DemoSignalRun>('/api/me/demo-signals/social-runs', { method: 'POST', body: { scenarioId } }),
  demoSignalReports: (kind: 'LOCATION' | 'SOCIAL') =>
    api<DemoSignalReport[]>(`/api/me/demo-signals/reports?kind=${kind}`),
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

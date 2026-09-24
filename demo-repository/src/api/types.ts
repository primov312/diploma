export type Partner = {
  id: number;
  slug: string;
  displayName: string;
  amountCap: number;
  currency: string;
};

export type Product = {
  id: number;
  fixtureId: string;
  name: string;
  price: number;
  currency: string;
};

export type PartnerDetail = Partner & { products: Product[] };

export type Transaction = {
  id: number;
  partnerSlug: string;
  partnerName: string;
  amount: number;
  currency: string;
  occurredOn: string;
  status: 'COMPLETED' | 'REFUNDED';
  paidOnTime: boolean;
  description: string | null;
};

export type FinancialProfile = {
  monthlyIncome: number;
  monthlyExpenses: number;
  monthlyObligations: number;
  profileComplete: boolean;
  emailVerified: boolean;
  syntheticSource: 'FIXTURE' | 'STARTER';
  accountAgeMonths: number;
};

export type FinancialInputs = {
  revision: number;
  monthlyNetIncome: number | null;
  housingSituation: 'RENTING' | 'OWNER' | 'FAMILY' | 'OTHER';
  expenseMode: 'AGGREGATE' | 'ITEMIZED';
  housingCost: number | null;
  groceriesCost: number | null;
  utilitiesCost: number | null;
  transportCost: number | null;
  otherLivingCosts: number | null;
  legacyLivingExpenses: number | null;
  monthlyObligations: number;
  source: 'STARTER' | 'FIXTURE' | 'USER_DECLARED';
  updatedAt: string;
};

export type SaveFinancialInputs = Omit<FinancialInputs, 'revision' | 'source' | 'updatedAt'> & {
  expectedRevision: number;
};

export type FinancialInputsSaveResult = {
  inputs: FinancialInputs;
  generation: number;
  recalculationJobId: number;
};

export type AffordabilityPartner = { slug: string; cap: number; possibleAmount: number | null };
export type AffordabilityEstimate = {
  status: 'READY' | 'UPDATING' | 'UNAVAILABLE' | 'ERROR';
  calculatedAt: string | null;
  generation: number;
  financialRevision: number;
  formulaVersion: string | null;
  policyVersion: string | null;
  currency: string;
  termMonths: number;
  baseAmount: number | null;
  monthlyPaymentCapacity: number | null;
  breakdown: Record<string, number>;
  partners: AffordabilityPartner[];
  reasons: string[];
  stale: boolean;
};
export type AffordabilityHistoryPoint = {
  month: string;
  calculatedAt: string | null;
  amount: number | null;
  partnerAmounts: Record<string, number | null>;
  formulaVersion: string | null;
  policyVersion: string | null;
  dataSource: string | null;
};
export type AnalysisJob = {
  id: number;
  state: 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'SUPERSEDED';
  attemptCount: number;
  failureCode: string | null;
  createdAt: string;
  completedAt: string | null;
};
export type RecalculationAccepted = { status: number; jobId: number };
export type Address = {
  available: boolean; revision: number; countryCode: string | null; city: string | null;
  districtId: string | null; postalCode: string | null; street: string | null;
  building: string | null; unit: string | null; verificationStatus: 'UNVERIFIED' | 'VERIFIED_DEMO' | 'NEEDS_REVIEW' | 'MISMATCH';
  verifiedAt: string | null;
};
export type DistrictCost = {
  districtId: string; displayName: string; city: string; countryCode: string; datasetVersion: string;
  monthlyRent: number; monthlyGroceries: number; citySalaryMonthly: number; salaryBasis: 'GROSS' | 'NET';
  currency: string; sourceLabel: string; observedAt: string; synthetic: boolean;
};
export type LocalCostExtraction = {
  status: 'EXTRACTED' | 'AI_UNAVAILABLE'; districtId: string; datasetVersion: string; dataSource: string;
  analysisMode: 'FIXTURE' | 'AI'; provider: string; modelVersion: string | null; promptVersion: string;
  monthlyRent: number | null; monthlyGroceries: number | null; citySalaryMonthly: number | null;
  salaryBasis: string | null; currency: string; evidencePassages: string[];
};
export type AddressSaveRequest = { expectedRevision: number; countryCode: string; city: string; districtId: string; postalCode: string; street: string; building: string; unit: string | null };
export type AddressSaveResult = { address: Address; generation: number; recalculationJobId: number };
export type AddressVerification = { id: number; addressRevision: number; status: string; scenarioId: string; dataSource: string; checks: string[]; createdAt: string; recalculationJobId: number };
export type DemoSignalSettings = { locationEnabled: boolean; socialEnabled: boolean; permissionGeneration: number };
export type DemoSignalReport = Record<string, unknown>;
export type DemoSignalRun = { jobId: number; state: string; report: DemoSignalReport | null };

export type DecisionStatus = 'APPROVED' | 'REJECTED' | 'REVIEW';
export type AiStatus = 'NOT_REQUESTED' | 'UNAVAILABLE' | 'APPLIED';

export type Factor = {
  score: number | null;
  weight: number;
  reasons: string[];
  details: Record<string, number | string | boolean | null>;
};

export type Application = {
  id: number;
  partnerSlug: string;
  partnerName: string;
  productId: number | null;
  productName: string | null;
  requestedAmount: number;
  currency: string;
  decisionStatus: DecisionStatus;
  score: number;
  possibleAmount: number;
  reasons: string[];
  factors: Record<string, Factor>;
  aiRequested: boolean;
  aiStatus: AiStatus;
  policyVersion: string;
  modelVersion: string | null;
  preparationMode: 'SEQUENTIAL' | 'PARALLEL';
  observedAt: string;
  createdAt: string;
  featureSnapshot?: Record<string, unknown>;
};

export type SubmitApplication = {
  partnerSlug: string;
  requestedAmount?: number;
  productId?: number;
  useAi: boolean;
};

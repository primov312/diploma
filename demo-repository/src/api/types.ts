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
  description: string;
  category: string;
  imagePath: string;
  imageAlt: string;
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
  expenseMode: 'AGGREGATE' | 'ITEMIZED' | 'AUTOMATIC';
  housingCost: number | null;
  groceriesCost: number | null;
  utilitiesCost: number | null;
  transportCost: number | null;
  otherLivingCosts: number | null;
  legacyLivingExpenses: number | null;
  monthlyObligations: number;
  source: 'STARTER' | 'FIXTURE' | 'USER_DECLARED' | 'AUTOMATIC';
  updatedAt: string;
};

export type HousingSituation = FinancialInputs['housingSituation'];
export type ExpenseMode = FinancialInputs['expenseMode'];

export type SaveFinancialInputs = { expectedRevision: number; housingSituation: HousingSituation; expenseMode: 'AUTOMATIC' };

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
  localCostContext?: MonthlyLivingCosts | null;
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
export type PostalCodeDistrict = { postalCode: string; districtId: string; displayName: string; city: string; countryCode: string; sourceUrl: string; retrievedAt: string; datasetVersion: string };
export type MonthlyCostProfile = { revision: number; datasetVersion: string; apartmentSize: number; rentSharers: number; groceryQuantities: Record<string, number | string>; otherSpending: number; source: 'DEMO_DEFAULT' | 'USER_DECLARED' };
export type MonthlyGroceryProduct = { id: string; label: string; description: string; unit: 'KG' | 'LITRE' | 'ITEM'; packageQuantity: string; packagePrice: string; unitPrice: string; sourceUrl: string; retrievedAt: string; observedAt: string | null };
export type ActivityForecast = { available: boolean; dataSource: string; forecastDays: number; baselineDatasetVersion?: string; reportDatasetVersion?: string; reportId?: number; method: string; reason: string; observationDays: number; pricedVisits: number; historySufficient: boolean; baselineMonthly: number; monthlyAmount: number; items: { category: string; baselineMonthly: number; historyMonthly?: number; monthlyAmount: number; defaultMonthlyVisits?: number; historyVisitCount: number; sourceUrl: string; unit: string; unitPrice: number; retrievedAt: string; method: string }[] };
export type MonthlyLivingCosts = {
  methodVersion?: string; activityForecast?: ActivityForecast; suppliedExpenseFloorHuf?: number;
  available: boolean; unavailableReason?: string; eligible: boolean; currency: 'HUF'; hufPerUsd: number;
  profile?: MonthlyCostProfile; groceries?: MonthlyGroceryProduct[]; district?: PostalCodeDistrict;
  housing?: { observedAt: string; retrievedAt: string; sourceUrl: string };
  datasetVersion?: string; housingDatasetVersion?: string; coff?: number; rentPerM2?: number;
  monthlyRent?: number; monthlyGroceries?: number; monthlyOther?: number; monthlyTotal?: number;
  rentApplies?: boolean; housingSituation?: string; addressRevision?: number; verificationStatus?: string; recalculationJobId?: number;
};
export type MonthlyCostDraft = { apartmentSize: string; rentSharers: number; groceryQuantities?: Record<string, string>; otherSpending?: string; postalCode?: string; expectedRevision?: number; refreshDataset?: boolean };
export type DemoSignalSettings = { locationEnabled: boolean; socialEnabled: boolean; permissionGeneration: number };
export type DemoSignalReport = Record<string, unknown>;

export type LocationVisitPricing = {
  districtId: string;
  category: 'GROCERY' | 'LIBRARY' | 'GYM' | 'CAFE' | 'STARBUCKS';
  coff: number;
  baselinePrice: number;
  estimatedPrice: number;
  currency: 'HUF';
  unit: 'BASKET' | 'YEAR' | 'ENTRY' | 'DRINK';
  description: string;
  sourceUrl: string;
  observedAt: string | null;
  retrievedAt: string;
  evidence: { channel: string; components?: { id: string; description: string; price: string; sourceUrl: string }[] };
};

export type LocationReportPricing = {
  available: boolean;
  method: string;
  datasetVersion?: string;
  housingObservedAt?: string;
  housingRetrievedAt?: string;
  districtAverage?: number;
  sourceUrl?: string;
  currency?: 'HUF';
};
export type SocialConnection = { configured: boolean; connected: boolean; provider: string | null; displayName: string | null; connectedAt: string | null; expiresAt: string | null };
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

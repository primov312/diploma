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

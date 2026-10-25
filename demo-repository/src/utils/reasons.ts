import type { AiStatus, DecisionStatus } from '../api/types';

/** Plain-language text for the analysis service's stable reason codes. */
const REASON_TEXT: Record<string, string> = {
  EVIDENCE_MISSING_PROFILE: 'We could not find your account profile, so an automatic decision was not possible.',
  EVIDENCE_MISSING_HISTORY: 'We could not read your purchase history, so an automatic decision was not possible.',
  EVIDENCE_MISSING_FINANCE: 'Your synthetic financial profile is missing, so affordability could not be checked.',
  PROFILE_ACCOUNT_NEW: 'Your account is very new.',
  PROFILE_INCOMPLETE: 'Your profile is not complete.',
  PROFILE_EMAIL_UNVERIFIED: 'Your email address is not verified.',
  PARTNER_TOO_FEW_COMPLETED_ORDERS: 'You have very few completed purchases with this seller.',
  PARTNER_ON_TIME_LOW: 'Some purchases with this seller were not paid on time.',
  PARTNER_REFUND_RATE_HIGH: 'Many of your purchases with this seller were refunded.',
  PARTNER_NO_HISTORY: 'You have no purchase history yet.',
  INSTALLMENT_ABOVE_CAPACITY: 'The selected installment is above your repayment capacity. Choose a longer period or a smaller amount.',
  ZERO_CAPACITY: 'Your synthetic income does not leave room for repayments at the moment.',
  AMOUNT_ABOVE_POSSIBLE_AMOUNT: 'The requested amount is above what your budget allows.',
  AMOUNT_ABOVE_POSSIBLE: 'The requested amount is above what your budget allows.',
  INCOME_MISSING: 'Your income is missing, so affordability could not be checked.',
  EXPENSES_MISSING: 'Your living costs are missing, so affordability could not be checked.',
  EXPENSES_INCOMPLETE: 'Some living costs are missing, so affordability could not be fully checked.',
  AUTOMATIC_COSTS_UNAVAILABLE: 'Living costs for your district are not available yet.',
  AUTOMATIC_POLICY_REQUIRED: 'Your budget needs to be recalculated before deciding.',
  // Budget notes: they explain which costs were used, not why a request was declined.
  SUPPLIED_DEMO_EXPENSE_BASELINE: 'Your demo profile’s living costs were used.',
  LOCAL_COSTS_NOT_APPLIED: 'Local prices are not used until your address is verified.',
  AUTOMATIC_LIVING_COSTS_APPLIED: 'Living costs for your district were used.',
  LOCAL_COST_FLOOR_APPLIED: 'Local prices set a minimum for your living costs.',
  DISTRICT_RENT_FLOOR_APPLIED: 'District rent prices set a minimum for your rent.',
  DISTRICT_GROCERY_FLOOR_APPLIED: 'District food prices set a minimum for groceries.',
  DISTRICT_OTHER_FLOOR_APPLIED: 'District prices set a minimum for other spending.',
  AMOUNT_ABOVE_PARTNER_CAP: 'The amount is above this seller’s credit limit.',
  AMOUNT_NEAR_LIMIT: 'The requested amount is close to your limit.',
  SCORE_LOW: 'Your overall score is below the approval range.',
  SCORE_INCONCLUSIVE: 'Your overall score is in the range where the automatic check cannot decide.',
  AI_FALLBACK_RULES_ONLY: 'AI analysis was requested but no model was available, so only the rules were used.',
  AI_RISK_ELEVATED: 'The AI model estimated an elevated risk for this request.',
};

const BUDGET_NOTES = new Set(['SUPPLIED_DEMO_EXPENSE_BASELINE', 'LOCAL_COSTS_NOT_APPLIED', 'AUTOMATIC_LIVING_COSTS_APPLIED',
  'LOCAL_COST_FLOOR_APPLIED', 'DISTRICT_RENT_FLOOR_APPLIED', 'DISTRICT_GROCERY_FLOOR_APPLIED', 'DISTRICT_OTHER_FLOOR_APPLIED']);
export const isBudgetNote = (code: string) => BUDGET_NOTES.has(code);

export const reasonText = (code: string): string => REASON_TEXT[code] ?? code.replaceAll('_', ' ').toLowerCase();

export const STATUS_TEXT: Record<DecisionStatus, { label: string; summary: string; tone: string }> = {
  APPROVED: {
    label: 'Approved',
    summary: 'You can afford this. Accept the plan below to pay the seller.',
    tone: 'bg-success-50 text-success-700 border-success-100',
  },
  REJECTED: {
    label: 'Not approved',
    summary: 'We could not approve this amount. See the reasons below.',
    tone: 'bg-error-50 text-error-700 border-error-100',
  },
  REVIEW: {
    label: 'Needs review',
    summary: 'The automatic check could not decide. In this demo there is no manual review, so the request stays open.',
    tone: 'bg-warning-50 text-warning-700 border-warning-100',
  },
};

export const AI_STATUS_TEXT: Record<AiStatus, string> = {
  NOT_REQUESTED: 'AI analysis was not requested; rules only.',
  UNAVAILABLE: 'AI analysis was requested but no model was available; rules only.',
  APPLIED: 'AI analysis contributed to this result.',
};

export const FACTOR_LABEL: Record<string, string> = {
  profile: 'Profile',
  history: 'History with this seller',
  affordability: 'Affordability',
  ai: 'AI risk estimate',
};

import type { AiStatus, DecisionStatus } from '../api/types';

/** Plain-language text for the analysis service's stable reason codes. */
const REASON_TEXT: Record<string, string> = {
  EVIDENCE_MISSING_PROFILE: 'We could not find your account profile, so an automatic decision was not possible.',
  EVIDENCE_MISSING_HISTORY: 'We could not read your purchase history, so an automatic decision was not possible.',
  EVIDENCE_MISSING_FINANCE: 'Your synthetic financial profile is missing, so affordability could not be checked.',
  PROFILE_ACCOUNT_NEW: 'Your account is very new.',
  PROFILE_INCOMPLETE: 'Your profile is not complete.',
  PROFILE_EMAIL_UNVERIFIED: 'Your email address is not verified.',
  PARTNER_TOO_FEW_COMPLETED_ORDERS: 'You have very few completed purchases with this store.',
  PARTNER_ON_TIME_LOW: 'Some purchases with this store were not paid on time.',
  PARTNER_REFUND_RATE_HIGH: 'A high share of your purchases with this store were refunded.',
  PARTNER_NO_HISTORY: 'You have no purchase history yet.',
  ZERO_CAPACITY: 'Your synthetic income does not leave room for repayments at the moment.',
  AMOUNT_ABOVE_POSSIBLE_AMOUNT: 'The requested amount is above what your budget allows.',
  AMOUNT_ABOVE_PARTNER_CAP: 'The requested amount is above this store’s financing limit.',
  AMOUNT_NEAR_LIMIT: 'The requested amount is close to your limit.',
  SCORE_LOW: 'Your overall score is below the approval range.',
  SCORE_INCONCLUSIVE: 'Your overall score is in the range where the automatic check cannot decide.',
  AI_FALLBACK_RULES_ONLY: 'AI analysis was requested but no model was available, so only the rules were used.',
};

export const reasonText = (code: string): string => REASON_TEXT[code] ?? code.replaceAll('_', ' ').toLowerCase();

export const STATUS_TEXT: Record<DecisionStatus, { label: string; summary: string; tone: string }> = {
  APPROVED: {
    label: 'Approved',
    summary: 'The automatic check found the requested amount within your possible amount. No money has been moved.',
    tone: 'bg-success-50 text-success-700 border-success-100',
  },
  REJECTED: {
    label: 'Not approved',
    summary: 'The automatic check could not approve this amount. If a lower possible amount is shown, you can submit a new request for it.',
    tone: 'bg-error-50 text-error-700 border-error-100',
  },
  REVIEW: {
    label: 'Needs review',
    summary: 'The automatic check was inconclusive. In this demonstration there is no manual review queue; the request stays as “needs review”.',
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
  history: 'Purchase history with this store',
  affordability: 'Affordability',
  ai: 'AI risk estimate',
};

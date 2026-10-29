// AI-assisted (Claude), 2026; reviewed by the author.
import type { Application } from '../../api/types';
import { cn } from '../../utils/cn';
import { formatCurrency } from '../../utils/format';
import { Icon } from '../common/Icon';

/** One-line label for lists: the human outcome of a REVIEW decision, if any. */
export const reviewLabel = (a: Application): string | null =>
  a.reviewState === 'APPROVED' ? 'approved after review' : a.reviewState === 'REJECTED' ? 'not approved after review'
    : a.reviewState === 'OPEN' ? 'waiting for review' : null;

/**
 * The administrator's decision on a request the automatic check could not decide. It is shown beside the
 * automatic result, which stays unchanged (human intervention, GDPR Art. 22(3)).
 */
export const HumanReview = ({ application: a }: { application: Application }) => {
  if (!a.reviewState) return null;
  if (a.reviewState === 'OPEN') {
    return <section aria-label="Human review" className="mb-6 rounded-lg border border-gray-200 bg-white p-4 text-sm text-gray-700 md:p-6">
      <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-800"><Icon name="clock" />Human review</h2>
      <p className="mt-2">An administrator will look at this request and decide. You will see the result here.</p>
    </section>;
  }
  const approved = a.reviewState === 'APPROVED';
  return <section aria-label="Human review" className={cn('mb-6 rounded-lg border p-4 md:p-6',
    approved ? 'border-success-100 bg-success-50' : 'border-error-100 bg-error-50')}>
    <h2 className="flex items-center gap-2 text-lg font-semibold text-gray-800"><Icon name={approved ? 'check' : 'close'} />
      Human review: {approved ? 'approved' : 'not approved'}</h2>
    {approved && a.reviewApprovedAmount != null && <p className="mt-2 text-2xl font-bold text-gray-900">{formatCurrency(a.reviewApprovedAmount)}</p>}
    {a.reviewNote && <p className="mt-2 text-gray-700"><span className="font-medium">Reviewer's note:</span> {a.reviewNote}</p>}
    <p className="mt-2 text-xs text-gray-600">An administrator decided this request after the automatic check could not. The automatic result below is kept as it was.</p>
  </section>;
};

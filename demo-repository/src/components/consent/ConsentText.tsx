// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
import type { ConsentItem } from '../../api/types';

/** The exact wording the customer agrees to, kept in one place so every screen shows the same text. */
export const ConsentText = ({ item }: { item: ConsentItem }) =>
  <details className="mt-2 text-sm text-gray-600">
    <summary className="cursor-pointer text-gray-700 underline">Read what I agree to</summary>
    <p className="mt-2 max-w-3xl leading-relaxed">{item.text}</p>
  </details>;

export const decidedText = (item: ConsentItem) => {
  if (!item.decidedAt) return 'No choice recorded yet.';
  const when = new Date(item.decidedAt).toLocaleString();
  const by = item.source === 'DEMO_FIXTURE' ? ' (recorded by the demo data loader)' : '';
  if (!item.current) return `You agreed on ${when} to an older wording, so please agree again${by}.`;
  return `${item.granted ? 'Agreed' : 'Withdrawn'} on ${when}${by}.`;
};

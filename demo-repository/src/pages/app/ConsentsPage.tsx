// AI-assisted: drafted with Claude (Anthropic), October 2026; listed in the Declaration on the use of AI tools.
import { useState } from 'react';
import { consentsApi } from '../../api/rocket';
import type { ConsentItem, ConsentPurpose } from '../../api/types';
import { Card, Loading, Notice, Page } from '../../components/app/Ui';
import { ConsentText, decidedText } from '../../components/consent/ConsentText';
import { errorMessage, useApi } from '../../hooks/useApi';

const EFFECT_OF_WITHDRAWING: Record<ConsentPurpose, string> = {
  DATA_PROCESSING: 'You will not be able to submit new credit requests. Decisions and repayment plans that already exist stay as a record.',
  AI_ANALYSIS: 'The AI risk estimate and AI-assisted analyses stop. The rules-based decision stays available.',
  DIGITAL_FOOTPRINT: 'Your stored social and location analysis results are deleted and your Facebook connection is removed.',
};

const ConsentRow = ({ item, busy, onChange }: { item: ConsentItem; busy: boolean; onChange: (granted: boolean) => void }) => {
  const id = `consent-${item.purpose}`;
  return <Card>
    <div className="flex flex-wrap items-start justify-between gap-4">
      <div className="min-w-0 flex-1">
        <h2 className="text-lg font-semibold text-gray-900">{item.title}</h2>
        <p className="mt-1 text-sm text-gray-500">{item.requiredForCredit ? 'Needed to apply for credit.' : 'Optional.'} {decidedText(item)}</p>
        <ConsentText item={item} />
        {item.granted && <p className="mt-2 text-sm text-gray-600">If you withdraw: {EFFECT_OF_WITHDRAWING[item.purpose]}</p>}
      </div>
      <label htmlFor={id} className="flex min-h-11 cursor-pointer items-center gap-3 text-sm font-medium">
        <input id={id} type="checkbox" role="switch" className="h-5 w-5" checked={item.granted} disabled={busy}
          onChange={(e) => onChange(e.target.checked)} aria-label={item.title} />
        {item.granted ? 'I agree' : 'Not agreed'}
      </label>
    </div>
  </Card>;
};

export default function ConsentsPage() {
  const consents = useApi(() => consentsApi.get(), []);
  const [busy, setBusy] = useState<ConsentPurpose | null>(null);
  const [error, setError] = useState('');

  const change = async (purpose: ConsentPurpose, granted: boolean) => {
    setBusy(purpose); setError('');
    try { await consentsApi.set(purpose, granted); consents.reload(); }
    catch (cause) { setError(cause instanceof Error ? errorMessage(cause) : 'Unable to save your choice.'); }
    finally { setBusy(null); }
  };

  return <Page title="Privacy and consent" subtitle="You decide what Rocket Credit may process. Every choice is recorded with the wording you saw, and you can change it at any time.">
    {consents.status === 'loading' && <Loading />}
    {consents.status === 'error' && <Notice tone="error">{errorMessage(consents.error)}</Notice>}
    {error && <div className="mb-4"><Notice tone="error">{error}</Notice></div>}
    {consents.status === 'ready' && <div className="space-y-4">
      {consents.data.consents.map((item) => <ConsentRow key={item.purpose} item={item} busy={busy !== null} onChange={(granted) => void change(item.purpose, granted)} />)}
      <p className="text-xs text-gray-500">Wording version {consents.data.policyVersion}. All data in this demo is synthetic, and the money is simulated.</p>
    </div>}
  </Page>;
}

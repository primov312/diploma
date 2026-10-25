import { LinkButton, Page } from '../components/app/Ui';
import { FlowSteps } from '../components/common/FlowSteps';
const FAQ = [
  ['How is my amount estimated?', 'From your income, debts and living costs for your Budapest district. Open “How this is calculated” on your dashboard to see every input.'],
  ['Why was I not approved?', 'Every decision lists its reasons, such as a short purchase history or an amount above your budget. Often a lower amount is suggested.'],
  ['What does the AI do?', 'Optionally, a trained model adds a risk estimate to the rules. The social analysis and the checks of uploaded address and housing documents use the hosted Jev model (TypeSafe), which only answers narrow yes/no and multiple-choice questions; it never decides on its own.'],
  ['Is my Facebook data used?', 'Only if you connect your account and run the analysis yourself. You can disconnect at any time.'],
];
const HowItWorks = () => <Page title="How it works" actions={<LinkButton to="/marketplace" icon="store">Browse marketplace</LinkButton>}>
  <FlowSteps />
  <div className="mt-10 max-w-3xl space-y-4"><h2 className="text-2xl font-semibold">Questions</h2>
    {FAQ.map(([q, a]) => <details key={q}><summary>{q}</summary><p>{a}</p></details>)}
  </div>
</Page>;
export default HowItWorks;

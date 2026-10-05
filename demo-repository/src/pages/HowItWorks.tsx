import { LinkButton, Page } from '../components/app/Ui';
import { FlowSteps } from '../components/common/FlowSteps';
const HowItWorks = () => <Page title="How it works" subtitle="Explore a purchase and review the result in three steps." actions={<LinkButton to="/stores" icon="store">Explore stores</LinkButton>}>
  <FlowSteps />
  <div className="mt-8 grid gap-6 lg:grid-cols-[1fr_1.4fr]"><div><h2 className="text-2xl font-semibold">Before you apply</h2><p className="mt-2 max-w-md text-gray-600">This demonstration uses synthetic financial data and illustrative catalogs. It does not provide real credit or process payments.</p></div>
    <div className="space-y-4">
      <details><summary>How is the affordable amount estimated?</summary><p>Your dashboard combines supplied financial data, saved housing information and local cost context. Open the calculation details on Overview to review its inputs.</p></details>
      <details><summary>Can I choose a product or enter an amount?</summary><p>Both. A catalog link carries your chosen product into the application. You can also start a request and enter an amount for the selected store.</p></details>
      <details><summary>Where is my decision saved?</summary><p>Applications lists your requests. Open one to review the outcome, amount and decision reasons.</p></details>
      <details><summary>What do the activity analyses use?</summary><p>Location and social demo analyses are always available. Clearly labeled synthetic scenarios let you explore their effects; connected Facebook content is used only when you select that source.</p></details>
    </div>
  </div>
</Page>;
export default HowItWorks;

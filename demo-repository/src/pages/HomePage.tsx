import { LinkButton, SyntheticTag } from '../components/app/Ui';
import { FlowSteps } from '../components/common/FlowSteps';
import { Icon } from '../components/common/Icon';
import PaymentCalculator from '../components/PaymentCalculator';

const HomePage = () => <div className="page-shell space-y-8">
  <section className="grid overflow-hidden rounded-lg border border-gray-200 bg-white lg:grid-cols-[1.15fr_1fr]">
    <div className="flex flex-col justify-between gap-8 bg-primary p-6 text-white md:p-10">
      <div><p className="eyebrow text-primary-200">Explore · Estimate · Apply</p><h1 className="mt-5 max-w-xl text-4xl font-semibold leading-tight md:text-5xl">Your next purchase.<br />A clearer decision.</h1><p className="mt-5 max-w-lg text-lg text-primary-100">Explore demo stores, check your affordable amount and review a saved financing decision.</p>
      <div className="mt-7 flex flex-wrap gap-3"><LinkButton to="/stores" variant="secondary" icon="store">Explore stores</LinkButton><LinkButton to="/account-dashboard" variant="secondary" icon="chart">Your account</LinkButton></div></div>
      <p className="flex items-start gap-2 text-sm text-primary-100"><Icon name="info" />Demo experience. No real purchases, lending or payments.</p>
    </div>
    <PaymentCalculator className="border-0 lg:p-8" />
  </section>
  <section aria-label="Getting started"><div className="mb-5 flex flex-wrap items-center justify-between gap-2"><h2 className="text-2xl font-semibold">From catalog to decision</h2><SyntheticTag>Illustrative demo</SyntheticTag></div><FlowSteps /></section>
</div>;
export default HomePage;

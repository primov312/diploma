import { LinkButton } from '../components/app/Ui';
import { FlowSteps } from '../components/common/FlowSteps';
import PaymentCalculator from '../components/PaymentCalculator';
import { useAuth } from '../auth/AuthContext';

const HomePage = () => {
  const { user } = useAuth();
  return <div className="page-shell space-y-10">
    <section className="grid overflow-hidden rounded-lg border border-gray-200 bg-white lg:grid-cols-[1.15fr_1fr]">
      <div className="flex flex-col justify-center gap-6 bg-primary p-6 text-white md:p-10">
        <h1 className="max-w-xl text-4xl font-semibold leading-tight md:text-5xl">Buy now, pay in parts.<br />See why you are approved.</h1>
        <p className="max-w-lg text-lg text-primary-100">Shop the marketplace, see what you can afford, and get an instant decision with clear reasons.</p>
        <div className="flex flex-wrap gap-3">
          <LinkButton to="/marketplace" variant="secondary" icon="store">Browse marketplace</LinkButton>
          {user ? <LinkButton to="/account-dashboard" variant="secondary" icon="chart">My dashboard</LinkButton>
            : <LinkButton to="/register" variant="secondary" icon="user">Create account</LinkButton>}
        </div>
      </div>
      <PaymentCalculator className="border-0 lg:p-8" />
    </section>
    <section aria-labelledby="steps-title"><h2 id="steps-title" className="mb-5 text-2xl font-semibold">How it works</h2><FlowSteps /></section>
  </div>;
};
export default HomePage;

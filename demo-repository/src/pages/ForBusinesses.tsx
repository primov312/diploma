// AI-assisted (Claude), 2026: seller accounts text updated; reviewed by the author.
import { Link } from 'react-router-dom';
import { LinkButton, Page } from '../components/app/Ui';
import { Icon } from '../components/common/Icon';
const STEPS = [
  { icon: 'store' as const, title: 'List products', text: 'Your catalog appears in the shared marketplace.' },
  { icon: 'file' as const, title: 'Customers apply', text: 'The product and price carry into the credit request.' },
  { icon: 'check' as const, title: 'You get paid', text: 'When the customer accepts, Rocket Credit pays you in full.' },
];
const ForBusinesses = () => <Page title="For businesses" actions={<LinkButton to="/marketplace" icon="store">See the marketplace</LinkButton>}>
  <h2 className="max-w-xl text-3xl font-semibold">Offer pay-in-parts without the risk.</h2>
  <ol className="mt-8 grid gap-6 sm:grid-cols-3">{STEPS.map((item, i) => <li key={item.title} className="border-t border-gray-200 pt-5"><p className="eyebrow text-accent">0{i + 1}</p><h3 className="mt-3 flex items-center gap-2 text-lg font-semibold"><Icon name={item.icon} />{item.title}</h3><p className="mt-2 text-sm text-gray-600">{item.text}</p></li>)}</ol>
  <p className="mt-10 text-sm text-gray-500">Demo sellers: Netflix, Amazon and Zara catalogs are illustrative and imply no affiliation.
    Seller accounts are created by a Rocket Credit administrator; there is no public seller sign-up.
    Sellers <Link to="/login" className="font-medium text-primary underline">sign in here</Link> to see their financed orders and manage their catalogue.</p>
</Page>;
export default ForBusinesses;

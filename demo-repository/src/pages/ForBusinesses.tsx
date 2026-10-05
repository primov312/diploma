import { Link } from 'react-router-dom';
import { LinkButton, Page, SyntheticTag } from '../components/app/Ui';
import { Icon } from '../components/common/Icon';
import { BRANDS } from './stores/brands';
const ForBusinesses = () => <Page title="For businesses" subtitle="Explore how a catalog product becomes a saved financing request." actions={<LinkButton to="/stores" icon="store">View catalogs</LinkButton>}>
  <section className="grid gap-6 border-b border-gray-200 pb-6 lg:grid-cols-[1fr_1.5fr]">
    <div><SyntheticTag>Demo integrations</SyntheticTag><h2 className="mt-4 max-w-md text-3xl font-semibold">A clear path from product to application.</h2><p className="mt-3 max-w-md text-gray-600">These catalogs demonstrate a product handoff into the customer account and financing flow.</p></div>
    <ol className="grid gap-5 sm:grid-cols-3">{[{icon:'store' as const,title:'Catalog',text:'Customers choose a product and see its illustrative price.'},{icon:'file' as const,title:'Application',text:'The selected store and product carry into a request.'},{icon:'check' as const,title:'Saved result',text:'Customers review their outcome and decision reasons.'}].map((item,i) => <li key={item.title}><p className="eyebrow text-accent">0{i+1}</p><h3 className="mt-3 flex items-center gap-2 text-lg font-semibold"><Icon name={item.icon} />{item.title}</h3><p className="mt-2 text-sm text-gray-600">{item.text}</p></li>)}</ol>
  </section>
  <section className="mt-6"><h2 className="mb-4 text-xl font-semibold">Explore a partner catalog</h2><div className="grid gap-4 md:grid-cols-3">{Object.values(BRANDS).map(brand => <Link key={brand.slug} to={'/stores/'+brand.slug} className="flex min-h-24 items-center justify-between gap-3 rounded-md border border-gray-200 bg-white p-5 hover:border-accent"><div><span className="text-xl font-bold tracking-tight">{brand.wordmark}</span><p className="text-sm text-gray-600">{brand.category}</p></div><Icon name="arrow" /></Link>)}</div></section>
  <details className="mt-6"><summary>About these demo integrations</summary><p>Brand names identify illustrative catalogs and do not imply affiliation. Products, prices and requests are demonstrations; no checkout, payment or merchant enrollment is provided.</p></details>
</Page>;
export default ForBusinesses;

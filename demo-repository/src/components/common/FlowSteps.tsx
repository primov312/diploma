import { Icon, type IconName } from './Icon';
const steps: {icon:IconName;title:string;text:string}[] = [
  {icon:'store',title:'Pick a product',text:'Browse the marketplace, or enter any amount.'},
  {icon:'clock',title:'Choose your payments',text:'1–3 weeks or 1–24 months. See every payment first.'},
  {icon:'check',title:'Get an instant decision',text:'With plain reasons. Accept only if it suits you.'},
];
export const FlowSteps = () => <ol className="grid gap-6 md:grid-cols-3">{steps.map((step,i) => <li key={step.title} className="flex gap-4 border-t border-gray-200 pt-5"><span className="text-sm font-semibold text-accent">0{i+1}</span><div><h3 className="flex items-center gap-2 text-lg font-semibold"><Icon name={step.icon} />{step.title}</h3><p className="mt-2 max-w-sm text-sm text-gray-600">{step.text}</p></div></li>)}</ol>;

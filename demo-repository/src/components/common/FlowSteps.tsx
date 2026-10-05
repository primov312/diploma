import { Icon, type IconName } from './Icon';
const steps: {icon:IconName;title:string;text:string}[] = [
  {icon:'user',title:'Sign in',text:'Open your account and review your financial information.'},
  {icon:'store',title:'Choose a product or amount',text:'Browse a demo catalog or start a financing request.'},
  {icon:'file',title:'Review your decision',text:'See the outcome and reasons in your saved applications.'},
];
export const FlowSteps = () => <ol className="grid gap-6 md:grid-cols-3">{steps.map((step,i) => <li key={step.title} className="flex gap-4 border-t border-gray-200 pt-5"><span className="text-sm font-semibold text-accent">0{i+1}</span><div><h2 className="flex items-center gap-2 text-lg font-semibold"><Icon name={step.icon} />{step.title}</h2><p className="mt-2 max-w-sm text-sm text-gray-600">{step.text}</p></div></li>)}</ol>;

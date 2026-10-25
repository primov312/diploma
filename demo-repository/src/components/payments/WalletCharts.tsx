import { useId } from 'react';
import type { WalletChart } from '../../api/payments';
import { money } from './PaymentUi';
export default function WalletCharts({ data }: { data: WalletChart }) {
  const id = useId();
  const max = Math.max(1, ...data.points.map(p => Number(p.balance)));
  const x = (index: number) => 80 + index * 600 / Math.max(1, data.points.length - 1);
  const y = (value: string) => 190 - Number(value) / max * 150;
  const line = data.points.map((p, i) => `${x(i)},${y(p.balance)}`).join(' ');
  const flowMax = Math.max(1, ...data.flows.flatMap(p => [Number(p.topUps), Number(p.purchases), Number(p.repayments)]));
  const colors = ['#246544', '#173450', '#A35F19'];
  return <div className="grid gap-8 xl:grid-cols-2">
    <section aria-labelledby={`${id}-balance`}><h3 id={`${id}-balance`} className="font-semibold">Balance over time</h3>
      <svg viewBox="0 0 720 230" role="img" aria-labelledby={`${id}-balance ${id}-desc`} className="w-full"><desc id={`${id}-desc`}>End-of-day balance. Values are available in the table below.</desc>
        {[0, 0.5, 1].map(n => <g key={n}><line x1="80" x2="680" y1={190 - n * 150} y2={190 - n * 150} stroke="#e2e8f0" /><text x="72" y={194 - n * 150} textAnchor="end" fontSize="11" fill="#526477">{money(max * n)}</text></g>)}
        <polyline points={line} fill="none" stroke="#173450" strokeWidth="3" />
        {data.points.filter((_, i) => i === 0 || i === data.points.length - 1).map((p, i) => <g key={p.date}><circle cx={i ? 680 : 80} cy={y(p.balance)} r="4" fill="#173450" /><text x={i ? 680 : 80} y="218" textAnchor={i ? 'end' : 'start'} fontSize="11">{p.date}</text></g>)}
      </svg><details><summary>Daily balance values</summary><div className="max-h-64 overflow-auto"><table className="w-full text-sm"><thead><tr><th className="text-left">Date</th><th className="text-right">Balance</th></tr></thead><tbody>{data.points.map(p => <tr key={p.date}><td>{p.date}</td><td className="text-right tabular-nums">{money(p.balance)}</td></tr>)}</tbody></table></div></details>
    </section>
    <section aria-labelledby={`${id}-flow`}><h3 id={`${id}-flow`} className="font-semibold">Money movement</h3>
      <div className="mt-2 flex flex-wrap gap-4 text-xs">{['Top-ups', 'Purchases', 'Repayments'].map((label, i) => <span key={label} className="flex items-center gap-1"><span className="inline-block h-2 w-2" style={{ background: colors[i] }} />{label}</span>)}</div>
      <svg viewBox="0 0 720 230" role="img" aria-labelledby={`${id}-flow ${id}-flow-desc`} className="w-full"><desc id={`${id}-flow-desc`}>Top-ups, purchases and repayments by period, with exact values in the table.</desc>
        {[0, 0.5, 1].map(n => <g key={n}><line x1="80" x2="680" y1={190 - n * 150} y2={190 - n * 150} stroke="#e2e8f0" /><text x="72" y={194 - n * 150} textAnchor="end" fontSize="11" fill="#526477">{money(flowMax * n)}</text></g>)}
        {data.flows.map((p, i) => { const slot = 600 / Math.max(1, data.flows.length), width = Math.max(1, slot / 4); return <g key={p.date}>{[p.topUps, p.purchases, p.repayments].map((v, j) => <rect key={j} x={80 + i * slot + j * width} y={190 - Number(v) / flowMax * 150} width={width} height={Number(v) / flowMax * 150} fill={colors[j]}><title>{p.date}: {['Top-ups', 'Purchases', 'Repayments'][j]} {money(v)}</title></rect>)}{(i === 0 || i === data.flows.length - 1) && <text x={i === 0 ? 80 : 680} y="218" textAnchor={i === 0 ? 'start' : 'end'} fontSize="11">{p.date}</text>}</g>; })}
      </svg><details><summary>Money movement values</summary><div className="max-h-64 overflow-auto"><table className="w-full text-sm"><thead><tr>{['Period starts', 'Top-ups', 'Purchases', 'Repayments'].map(h => <th key={h} className="text-left">{h}</th>)}</tr></thead><tbody>{data.flows.map(p => <tr key={p.date}><td>{p.date}</td><td>{money(p.topUps)}</td><td>{money(p.purchases)}</td><td>{money(p.repayments)}</td></tr>)}</tbody></table></div></details>
    </section>
  </div>;
}

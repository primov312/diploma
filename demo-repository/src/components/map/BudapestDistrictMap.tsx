import { useId, useMemo, useRef, useState, type PointerEvent } from 'react';
import { BUDAPEST_DISTRICTS, BUDAPEST_VIEWBOX, DANUBE_PATHS } from './budapestDistricts';

/** Sequential ramp: primary 100 → 700 (one hue, light to dark). Districts without visits stay neutral. */
const RAMP = ['#DCE6F0', '#8BA9C7', '#577DA3', '#355D85', '#244364'];
const NO_VISITS = '#F1F4F8';
const INK = '#13243B';
const SMALL = new Set(['I', 'V', 'VI', 'VII', 'VIII']);
const IDS = new Set(BUDAPEST_DISTRICTS.map((district) => district.id));
const [, , VIEW_W, VIEW_H] = BUDAPEST_VIEWBOX.split(' ').map(Number);

/** "Budapest V", "District XIII", "budapest-xi", "V. kerület" → "V"; anything else → null. */
export const districtId = (value: unknown): string | null => {
  if (typeof value !== 'string') return null;
  return value.toUpperCase().split(/[^A-Z]+/).find((token) => IDS.has(token)) ?? null;
};

export const countVisits = (districts: unknown[]) => districts.reduce<Record<string, number>>((counts, value) => {
  const id = districtId(value);
  if (id) counts[id] = (counts[id] ?? 0) + 1;
  return counts;
}, {});

const level = (count: number, max: number) => count <= 0 ? -1 : Math.min(RAMP.length - 1, Math.ceil((count / max) * RAMP.length) - 1);

type Props = {
  counts: Record<string, number>;
  home?: string | null;
  selected?: string | null;
  onSelect?: (id: string | null) => void;
};

/** Choropleth of Budapest's 23 districts: darker means more visits; the most visited one is outlined. */
export function BudapestDistrictMap({ counts, home, selected, onSelect }: Props) {
  const clip = useId();
  const frame = useRef<HTMLDivElement>(null);
  const [hover, setHover] = useState<{ id: string; x: number; y: number } | null>(null);
  const max = Math.max(0, ...Object.values(counts));
  const top = useMemo(() => Object.entries(counts).sort((a, b) => b[1] - a[1])[0]?.[0] ?? null, [counts]);
  const label = (id: string) => `District ${id}: ${counts[id] ?? 0} visit${counts[id] === 1 ? '' : 's'}${id === top ? ' · most visited' : ''}${id === home ? ' · your address' : ''}`;

  const placeAt = (id: string, event?: PointerEvent) => {
    const box = frame.current?.getBoundingClientRect();
    if (!box) return;
    if (event) return setHover({ id, x: event.clientX - box.left, y: event.clientY - box.top });
    const district = BUDAPEST_DISTRICTS.find((item) => item.id === id)!;
    setHover({ id, x: (district.label[0] / VIEW_W) * box.width, y: (district.label[1] / VIEW_H) * box.height });
  };

  return <figure className="m-0">
    <div ref={frame} className="relative" onPointerLeave={() => setHover(null)}>
      <svg viewBox={BUDAPEST_VIEWBOX} className="h-auto w-full" role="group" aria-label="Map of Budapest districts shaded by number of visits">
        <defs><clipPath id={clip}>{BUDAPEST_DISTRICTS.map((d) => <path key={d.id} d={d.path} />)}</clipPath></defs>
        {BUDAPEST_DISTRICTS.map((d) => {
          const step = level(counts[d.id] ?? 0, max);
          return <path key={d.id} d={d.path} fill={step < 0 ? NO_VISITS : RAMP[step]} stroke="#FAFBFD" strokeWidth={3} strokeLinejoin="round" />;
        })}
        <g clipPath={`url(#${clip})`} fill="none" stroke={INK} strokeWidth={9} strokeLinecap="round" strokeLinejoin="round" opacity={0.85}>
          {DANUBE_PATHS.map((path, i) => <path key={i} d={path} />)}
        </g>
        {BUDAPEST_DISTRICTS.filter((d) => d.id === home && d.id !== top).map((d) =>
          <path key="home" d={d.path} fill="none" stroke="#8A570C" strokeWidth={5} strokeDasharray="12 8" />)}
        {BUDAPEST_DISTRICTS.filter((d) => d.id === top || d.id === selected).map((d) =>
          <path key={`edge-${d.id}`} d={d.path} fill="none" stroke={INK} strokeWidth={d.id === selected ? 7 : 5} />)}
        {BUDAPEST_DISTRICTS.map((d) => {
          const dark = level(counts[d.id] ?? 0, max) >= 2;
          return <text key={`t-${d.id}`} x={d.label[0]} y={d.label[1]} textAnchor="middle" dominantBaseline="central"
            fontSize={SMALL.has(d.id) ? 19 : 26} fontWeight={700} fill={dark ? '#FAFBFD' : INK} pointerEvents="none">{d.id}.</text>;
        })}
        {BUDAPEST_DISTRICTS.map((d) => <path key={`hit-${d.id}`} d={d.path} fill="transparent"
          className={onSelect ? 'cursor-pointer outline-none' : 'outline-none'}
          tabIndex={0} role={onSelect ? 'button' : 'img'} aria-label={label(d.id)} aria-pressed={onSelect ? selected === d.id : undefined}
          onPointerMove={(event) => placeAt(d.id, event)} onFocus={() => placeAt(d.id)} onBlur={() => setHover(null)}
          onClick={() => onSelect?.(selected === d.id ? null : d.id)}
          onKeyDown={(event) => { if (onSelect && (event.key === 'Enter' || event.key === ' ')) { event.preventDefault(); onSelect(selected === d.id ? null : d.id); } }} />)}
      </svg>
      {hover && <div role="status" className="pointer-events-none absolute z-10 -translate-x-1/2 -translate-y-full whitespace-nowrap rounded-md bg-primary px-3 py-2 text-xs text-white"
        style={{ left: hover.x, top: hover.y - 10 }}>
        <strong className="block text-sm">District {hover.id}</strong>
        {counts[hover.id] ?? 0} visit{counts[hover.id] === 1 ? '' : 's'}{hover.id === top ? ' · most visited' : ''}{hover.id === home ? ' · your address' : ''}
      </div>}
    </div>
    <figcaption className="mt-3 flex flex-wrap items-center gap-x-5 gap-y-2 text-xs text-gray-600">
      <span className="flex items-center gap-2">Visits
        <span className="flex items-center gap-1" aria-hidden="true">
          <span className="h-3 w-4 rounded-sm border border-gray-300" style={{ background: NO_VISITS }} />
          {RAMP.map((color) => <span key={color} className="h-3 w-4 rounded-sm" style={{ background: color }} />)}
        </span>
        <span>0 → {max || 0}</span>
      </span>
      {top && <span className="flex items-center gap-1.5"><span className="h-3 w-4 rounded-sm border-2" style={{ borderColor: INK }} aria-hidden="true" />Most visited</span>}
      {home && <span className="flex items-center gap-1.5"><span className="h-3 w-4 rounded-sm border-2 border-dashed border-warning-700" aria-hidden="true" />Your address</span>}
      <span className="ml-auto text-gray-500">Map © <a className="underline" href="https://www.openstreetmap.org/copyright" target="_blank" rel="noreferrer">OpenStreetMap</a></span>
    </figcaption>
  </figure>;
}

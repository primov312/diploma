import type { SVGProps } from 'react';
const paths = {
  home:'M3 10l9-7 9 7v10H3V10 M9 20v-7h6v7',
  wallet:'M3 6h17v14H3V6 M3 6V4h14v2 M16 11h5v5h-5z',
  pin:'M19 10c0 5-7 11-7 11S5 15 5 10a7 7 0 1114 0z M14 10a2 2 0 11-4 0 2 2 0 014 0z',
  activity:'M3 12h4l3-8 4 16 3-8h4',
  user:'M16 7a4 4 0 11-8 0 4 4 0 018 0z M4 21v-2a8 8 0 0116 0v2',
  store:'M3 10l2-7h14l2 7 M3 10v11h18V10 M3 10c0 3 4 3 4 0 0 3 5 3 5 0 0 3 5 3 5 0 0 3 4 3 4 0 M9 21v-7h6v7',
  file:'M14 3H5v18h14V8l-5-5z M14 3v5h5 M8 12h8 M8 16h6',
  chart:'M4 3v18h17 M8 16l4-5 4 2 5-7',
  arrow:'M4 12h16 M14 6l6 6-6 6',
  check:'M5 12l4 4L19 6',
  clock:'M21 12a9 9 0 11-18 0 9 9 0 0118 0z M12 7v5l3 2',
  info:'M21 12a9 9 0 11-18 0 9 9 0 0118 0z M12 11v6 M12 7h.01',
  close:'M6 6l12 12 M6 18L18 6',
  menu:'M4 6h16 M4 12h16 M4 18h16',
  logout:'M9 4H4v16h5 M10 12h11 M16 7l5 5-5 5',
  shield:'M12 3l8 3v6c0 5-8 9-8 9s-8-4-8-9V6l8-3z M8 12l3 3 5-6',
  search:'M16 10a6 6 0 11-12 0 6 6 0 0112 0z M15 15l6 6',
  building:'M4 21V3h11v18 M15 9h5v12 M8 7h3 M8 11h3 M8 15h3 M2 21h20',
  layers:'M12 3l10 5-10 5L2 8l10-5z M2 12l10 5 10-5 M2 16l10 5 10-5',
} as const;
export type IconName = keyof typeof paths;
export const Icon = ({ name, className = 'h-5 w-5 shrink-0', ...props }: SVGProps<SVGSVGElement> & { name:IconName }) => <svg {...props} className={className} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.75" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true" focusable="false"><path d={paths[name]} /></svg>;

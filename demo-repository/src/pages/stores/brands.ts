/** Display treatments for the private academic demo. Internal partner slugs remain stable. */
export type Brand = {
  slug: string;
  name: string;
  tagline: string;
  blurb: string;
  accent: string;       // Tailwind bg class for the hero
  text: string;         // text colour on the hero
  button: string;       // button classes
  wordmark: string;
  category: string;
};

export const BRANDS: Record<string, Brand> = {
  streambox: {
    slug: 'streambox',
    name: 'Netflix',
    tagline: 'Illustrative films, series and viewing plans.',
    blurb: 'A fictional catalog of titles and viewing packages for the diploma demonstration. Titles and prices are not real Netflix offers.',
    accent: 'bg-gradient-to-br from-red-700 to-neutral-950',
    text: 'text-white',
    button: 'bg-red-700 text-white hover:bg-red-800',
    wordmark: 'NETFLIX',
    category: 'Entertainment',
  },
  markethub: {
    slug: 'markethub',
    name: 'Amazon',
    tagline: 'Illustrative electronics, home and travel goods.',
    blurb: 'Demo-only product entries and prices inspired by a broad online marketplace. These are not official Amazon listings.',
    accent: 'bg-gradient-to-br from-slate-900 to-slate-700',
    text: 'text-white',
    button: 'bg-slate-900 text-white hover:bg-slate-800',
    wordmark: 'amazon',
    category: 'Electronics & home',
  },
  threadly: {
    slug: 'threadly',
    name: 'Zara',
    tagline: 'Illustrative clothing and accessories.',
    blurb: 'A demo fashion catalog with fictional prices and illustrative photos. These are not official Zara listings.',
    accent: 'bg-gradient-to-br from-stone-200 to-stone-400',
    text: 'text-stone-950',
    button: 'bg-stone-950 text-white hover:bg-stone-800',
    wordmark: 'ZARA',
    category: 'Fashion',
  },
};

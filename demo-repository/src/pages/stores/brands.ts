/** Branding for the three demo stores. Catalog data comes from the backend; only the look lives here. */
export type Brand = {
  slug: string;
  name: string;
  tagline: string;
  blurb: string;
  accent: string;       // Tailwind bg class for the hero
  text: string;         // text colour on the hero
  button: string;       // button classes
  emoji: string;
  category: string;
};

export const BRANDS: Record<string, Brand> = {
  streambox: {
    slug: 'streambox',
    name: 'StreamBox',
    tagline: 'Films, series and live sport. One subscription.',
    blurb: 'A streaming service. Annual plans are the typical financing case: pay the year over time.',
    accent: 'bg-gradient-to-br from-indigo-700 to-violet-600',
    text: 'text-white',
    button: 'bg-white text-indigo-700 hover:bg-indigo-50',
    emoji: '🎬',
    category: 'Entertainment',
  },
  markethub: {
    slug: 'markethub',
    name: 'MarketHub',
    tagline: 'Electronics and home. Delivered tomorrow.',
    blurb: 'An electronics marketplace with the highest ticket sizes of the three demo stores.',
    accent: 'bg-gradient-to-br from-amber-400 to-orange-500',
    text: 'text-gray-900',
    button: 'bg-gray-900 text-white hover:bg-gray-800',
    emoji: '🛒',
    category: 'Electronics & home',
  },
  threadly: {
    slug: 'threadly',
    name: 'Threadly',
    tagline: 'Everyday clothing, made to last.',
    blurb: 'A clothing store. Mid-size baskets and a seasonal rhythm.',
    accent: 'bg-gradient-to-br from-emerald-600 to-teal-500',
    text: 'text-white',
    button: 'bg-white text-emerald-700 hover:bg-emerald-50',
    emoji: '👕',
    category: 'Fashion',
  },
};

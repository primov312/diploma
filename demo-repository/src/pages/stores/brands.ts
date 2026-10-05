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
    accent: 'bg-primary',
    text: 'text-white',
    button: 'btn-primary',
    wordmark: 'NETFLIX',
    category: 'Entertainment',
  },
  markethub: {
    slug: 'markethub',
    name: 'Amazon',
    tagline: 'Illustrative electronics, home and travel goods.',
    blurb: 'Demo-only product entries and prices inspired by a broad online marketplace. These are not official Amazon listings.',
    accent: 'bg-primary',
    text: 'text-white',
    button: 'btn-primary',
    wordmark: 'amazon',
    category: 'Electronics & home',
  },
  threadly: {
    slug: 'threadly',
    name: 'Zara',
    tagline: 'Illustrative clothing and accessories.',
    blurb: 'A demo fashion catalog with fictional prices and illustrative photos. These are not official Zara listings.',
    accent: 'bg-primary',
    text: 'text-white',
    button: 'btn-primary',
    wordmark: 'ZARA',
    category: 'Fashion',
  },
};

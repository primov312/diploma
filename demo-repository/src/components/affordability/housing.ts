import type { HousingSituation } from '../../api/types';
import rentingSample from '../../assets/sample-housing-renting.png';
import ownerSample from '../../assets/sample-housing-owner.png';
import familySample from '../../assets/sample-housing-family.png';
import otherSample from '../../assets/sample-housing-other.png';

export const HOUSING_LABELS: Record<HousingSituation, string> = {
  RENTING: 'Renting', OWNER: 'Owner', FAMILY: 'Living with family', OTHER: 'Other',
};

/** The document that supports each situation, and a synthetic sample of it for the demo. */
export const HOUSING_DOCUMENTS: Record<HousingSituation, { name: string; sample: string; alt: string }> = {
  RENTING: { name: 'a rental agreement', sample: rentingSample,
    alt: 'Synthetic residential lease agreement: tenant Riley Review, Minta utca 12, 1051 Budapest. Marked sample only.' },
  OWNER: { name: 'an ownership certificate (land registry extract) or a purchase contract', sample: ownerSample,
    alt: 'Synthetic property ownership certificate: registered owner Riley Review, Minta utca 12, 1051 Budapest. Marked sample only.' },
  FAMILY: { name: 'a family residence declaration', sample: familySample,
    alt: 'Synthetic family residence declaration: Riley Review lives rent-free with a parent at Minta utca 12, 1051 Budapest. Marked sample only.' },
  OTHER: { name: 'an accommodation confirmation', sample: otherSample,
    alt: 'Synthetic accommodation confirmation: resident Riley Review, Minta utca 12, 1051 Budapest. Marked sample only.' },
};

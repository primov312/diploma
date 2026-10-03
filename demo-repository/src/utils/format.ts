/** The backend stores and returns all amounts in USD; the UI shows and accepts HUF. */
export const USD_TO_HUF = 360;

const HUF_FORMATTER = new Intl.NumberFormat('hu-HU', {
  style: 'currency',
  currency: 'HUF',
  maximumFractionDigits: 0,
  minimumFractionDigits: 0,
});

/** Formats an amount that is already in HUF. */
export const formatHuf = (value: number): string => HUF_FORMATTER.format(value);

/** Converts a USD amount from the API to HUF, rounded to forints. */
export const usdToHuf = (usd: number): number => usd * USD_TO_HUF;

/** Converts a HUF amount entered by the user to USD with cents, as the API expects. */
export const hufToUsd = (huf: number): number => Math.round((huf / USD_TO_HUF) * 100) / 100;

/** Formats a USD amount from the API as HUF. */
export const formatCurrency = (value: number): string => formatHuf(usdToHuf(value));

export default formatCurrency;

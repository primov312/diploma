const CURRENCY_FORMATTER = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  maximumFractionDigits: 2,
  minimumFractionDigits: 2,
});

export const formatCurrency = (value: number): string => CURRENCY_FORMATTER.format(value);

export default formatCurrency;

type ClassDictionary = Record<string, boolean | null | undefined>;

export type ClassValue =
  | string
  | number
  | null
  | false
  | undefined
  | ClassDictionary
  | ClassValue[];

const isDictionary = (value: ClassValue): value is ClassDictionary =>
  typeof value === 'object' && value !== null && !Array.isArray(value);

export const cn = (...inputs: ClassValue[]): string => {
  const classes: string[] = [];

  inputs.forEach((input) => {
    if (!input) return;

    if (typeof input === 'string' || typeof input === 'number') {
      classes.push(String(input));
      return;
    }

    if (Array.isArray(input)) {
      const nested = cn(...input);
      if (nested) {
        classes.push(nested);
      }
      return;
    }

    if (isDictionary(input)) {
      Object.entries(input).forEach(([className, condition]) => {
        if (condition) {
          classes.push(className);
        }
      });
    }
  });

  return classes.join(' ');
};

export default cn;

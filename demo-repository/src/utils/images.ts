import type { SyntheticEvent } from 'react';

export const applyImageFallback = (event: SyntheticEvent<HTMLImageElement>): void => {
  const { currentTarget } = event;
  const fallback = currentTarget.dataset.fallback;

  if (!fallback || currentTarget.src === fallback) {
    return;
  }

  currentTarget.src = fallback;
  currentTarget.dataset.fallback = '';
  currentTarget.onerror = null;
};

export default applyImageFallback;

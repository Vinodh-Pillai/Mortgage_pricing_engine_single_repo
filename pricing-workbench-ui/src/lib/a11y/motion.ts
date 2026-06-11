import { useEffect, useState } from 'react';

export const reducedMotionQuery = '(prefers-reduced-motion: reduce)';

export function prefersReducedMotion() {
  return typeof window !== 'undefined' && 'matchMedia' in window && window.matchMedia(reducedMotionQuery).matches;
}

export function useReducedMotion() {
  const [reducedMotion, setReducedMotion] = useState(prefersReducedMotion);

  useEffect(() => {
    if (!('matchMedia' in window)) return undefined;
    const mediaQuery = window.matchMedia(reducedMotionQuery);
    const listener = () => setReducedMotion(mediaQuery.matches);
    listener();
    mediaQuery.addEventListener('change', listener);
    return () => mediaQuery.removeEventListener('change', listener);
  }, []);

  return reducedMotion;
}

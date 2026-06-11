import { useEffect, useState } from 'react';
import { breakpoints } from '../../design-system';

export type Breakpoint = 'mobile' | 'tablet' | 'desktop' | 'wide';

const tablet = Number.parseInt(breakpoints.tablet, 10);
const desktop = Number.parseInt(breakpoints.desktop, 10);
const wide = Number.parseInt(breakpoints.wide, 10);

export function getBreakpointForWidth(width: number): Breakpoint {
  if (width >= wide) return 'wide';
  if (width >= desktop) return 'desktop';
  if (width >= tablet) return 'tablet';
  return 'mobile';
}

export function useBreakpoint() {
  const [breakpoint, setBreakpoint] = useState<Breakpoint>(() => getBreakpointForWidth(typeof window === 'undefined' ? desktop : window.innerWidth));

  useEffect(() => {
    const update = () => setBreakpoint(getBreakpointForWidth(window.innerWidth));
    update();
    window.addEventListener('resize', update);
    return () => window.removeEventListener('resize', update);
  }, []);

  return breakpoint;
}

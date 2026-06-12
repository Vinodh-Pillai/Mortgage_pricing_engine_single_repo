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

function readCurrentBreakpoint(): Breakpoint {
  if (typeof window === 'undefined') return getBreakpointForWidth(desktop);
  return getBreakpointForWidth(window.innerWidth);
}

export function useBreakpoint() {
  const [breakpoint, setBreakpoint] = useState<Breakpoint>(readCurrentBreakpoint);

  useEffect(() => {
    if (typeof window === 'undefined') return undefined;
    let animationFrame: number | null = null;
    const commitBreakpoint = () => {
      animationFrame = null;
      setBreakpoint((current) => {
        const next = readCurrentBreakpoint();
        return current === next ? current : next;
      });
    };
    const update = () => {
      if (typeof requestAnimationFrame !== 'function') {
        commitBreakpoint();
        return;
      }
      if (animationFrame !== null) return;
      animationFrame = requestAnimationFrame(commitBreakpoint);
    };
    update();
    window.addEventListener('resize', update);
    return () => {
      if (animationFrame !== null && typeof cancelAnimationFrame === 'function') cancelAnimationFrame(animationFrame);
      window.removeEventListener('resize', update);
    };
  }, []);

  return breakpoint;
}

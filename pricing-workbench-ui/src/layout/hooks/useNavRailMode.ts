import { useBreakpoint } from './useBreakpoint';

export type NavRailMode = 'rail' | 'drawer';

export function useNavRailMode(): NavRailMode {
  const breakpoint = useBreakpoint();
  return breakpoint === 'mobile' ? 'drawer' : 'rail';
}

import { useBreakpoint } from './useBreakpoint';

export type NavRailMode = 'rail' | 'drawer';

export function useNavRailMode(): NavRailMode {
  return useBreakpoint() === 'mobile' ? 'drawer' : 'rail';
}

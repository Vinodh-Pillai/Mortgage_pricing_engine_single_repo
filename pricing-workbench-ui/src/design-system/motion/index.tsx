import type { ReactNode } from 'react';

export const motionNames = ['fade-in', 'slide-up', 'slide-down', 'scale-in', 'shimmer'] as const;
export const reducedMotionQuery = '(prefers-reduced-motion: reduce)';

export function AnimatePresence({ children }: { children: ReactNode }) {
  return <>{children}</>;
}

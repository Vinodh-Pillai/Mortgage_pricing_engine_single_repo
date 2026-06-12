import type { ReactNode } from 'react';
import { motion } from '../tokens';

export const motionNames = Object.values(motion.keyframes);
export const reducedMotionQuery = '(prefers-reduced-motion: reduce)';
export const stagger = motion.stagger;

export function AnimatePresence({ children }: { children: ReactNode }) {
  return <>{children}</>;
}

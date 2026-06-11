import { describe, expect, it } from 'vitest';
import { motionNames, reducedMotionQuery } from './motion';
import { motion } from './tokens';

describe('MotionTest', () => {
  it('reducedMotionDisablesAnimations', () => {
    expect(reducedMotionQuery).toBe('(prefers-reduced-motion: reduce)');
    expect(motion.duration.instant).toBe('0ms');
    expect(motionNames).toContain('shimmer');
  });
});

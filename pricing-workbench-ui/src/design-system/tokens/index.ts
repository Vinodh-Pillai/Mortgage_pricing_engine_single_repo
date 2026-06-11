import { borderRadius } from './borderRadius';
import { breakpoints } from './breakpoints';
import { colors } from './colors';
import { motion } from './motion';
import { shadows } from './shadows';
import { spacing } from './spacing';
import { typography } from './typography';
import { zIndex } from './zIndex';

export { borderRadius, breakpoints, colors, motion, shadows, spacing, typography, zIndex };

export const designTokens = {
  colors,
  spacing,
  typography,
  borderRadius,
  shadows,
  zIndex,
  breakpoints,
  motion,
} as const;

export const cssVariableMap = {
  '--ds-color-background': colors.dark.background,
  '--ds-color-surface': colors.dark.surface,
  '--ds-color-border': colors.dark.border,
  '--ds-color-text': colors.dark.text,
  '--ds-space-4': spacing[4],
  '--ds-radius-md': borderRadius.md,
  '--ds-shadow-2': shadows[2],
  '--ds-motion-normal': motion.duration.normal,
} as const;

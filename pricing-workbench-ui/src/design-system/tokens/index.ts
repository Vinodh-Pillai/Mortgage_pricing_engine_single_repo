import { borderRadius } from './borderRadius';
import { breakpoints } from './breakpoints';
import { colors } from './colors';
import { glass } from './glass';
import { motion } from './motion';
import { roleColors } from './roleColors';
import { shadows } from './shadows';
import { spacing } from './spacing';
import { typography } from './typography';
import { zIndex } from './zIndex';

export { borderRadius, breakpoints, colors, glass, motion, roleColors, shadows, spacing, typography, zIndex };
export { roleColorKeyForLabel, type RoleColor, type RoleColorKey } from './roleColors';
export { designTokenSchema, validateDesignTokens } from './validation';

export const designTokens = {
  colors,
  glass,
  roleColors,
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
  '--ds-glass-background': glass.background,
  '--ds-glass-border': glass.border,
  '--ds-glass-shadow': glass.shadow,
  '--ds-glass-blur': glass.blur,
  '--ds-glass-blur-strong': glass.blurStrong,
  '--ds-glass-background-strong': glass.backgroundStrong,
  '--ds-glass-border-strong': glass.borderStrong,
  '--ds-glass-shadow-strong': glass.shadowStrong,
  '--ds-glass-shadow-colored': glass.shadowColored,
  '--ds-glass-blur-subtle': glass.blurSubtle,
  '--ds-role-loan-officer-bg': roleColors['loan-officer'].bg,
  '--ds-role-loan-officer-text': roleColors['loan-officer'].text,
  '--ds-role-loan-officer-border': roleColors['loan-officer'].border,
  '--ds-role-pricing-analyst-bg': roleColors['pricing-analyst'].bg,
  '--ds-role-pricing-analyst-text': roleColors['pricing-analyst'].text,
  '--ds-role-pricing-analyst-border': roleColors['pricing-analyst'].border,
  '--ds-motion-normal': motion.durations.normal,
  '--ds-motion-stagger-base': motion.stagger.base,
} as const;

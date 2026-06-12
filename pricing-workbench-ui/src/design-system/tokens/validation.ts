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

type SchemaProperty = { type: string; required?: string[]; additionalProperties?: boolean; properties?: Record<string, SchemaProperty> };

export const designTokenSchema = {
  $schema: 'https://json-schema.org/draft/2020-12/schema',
  title: 'PricingWorkbenchDesignTokens',
  type: 'object',
  required: ['colors', 'spacing', 'typography', 'borderRadius', 'shadows', 'zIndex', 'breakpoints', 'motion', 'glass', 'roleColors'],
  properties: {
    colors: { type: 'object' },
    spacing: { type: 'object' },
    typography: { type: 'object' },
    borderRadius: { type: 'object' },
    shadows: { type: 'object' },
    zIndex: { type: 'object' },
    breakpoints: { type: 'object' },
    motion: { type: 'object', required: ['durations', 'easings', 'stagger', 'keyframes'] },
    glass: { type: 'object', required: ['background', 'backgroundStrong', 'border', 'borderStrong', 'shadow', 'shadowStrong', 'shadowColored', 'blur', 'blurStrong', 'blurSubtle'] },
    roleColors: { type: 'object' },
  } satisfies Record<string, SchemaProperty>,
} as const;

const defaultTokens = { colors, spacing, typography, borderRadius, shadows, zIndex, breakpoints, motion, glass, roleColors } as const;

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

export function validateDesignTokens(tokens: unknown = defaultTokens): { valid: boolean; errors: string[] } {
  const errors: string[] = [];
  if (!isObject(tokens)) return { valid: false, errors: ['tokens must be an object'] };

  for (const family of designTokenSchema.required) {
    if (!isObject(tokens[family])) errors.push(`${family} must be an object`);
  }

  const tokenRecord = tokens as typeof defaultTokens;
  for (const key of designTokenSchema.properties.glass.required ?? []) {
    if (typeof tokenRecord.glass?.[key as keyof typeof glass] !== 'string') errors.push(`glass.${key} must be a string`);
  }
  for (const key of designTokenSchema.properties.motion.required ?? []) {
    if (!isObject(tokenRecord.motion?.[key as keyof typeof motion])) errors.push(`motion.${key} must be an object`);
  }
  for (const [role, value] of Object.entries(tokenRecord.roleColors ?? {})) {
    if (!isObject(value) || typeof value.bg !== 'string' || typeof value.text !== 'string' || typeof value.border !== 'string') {
      errors.push(`roleColors.${role} must include bg, text, and border strings`);
    }
  }

  return { valid: errors.length === 0, errors };
}

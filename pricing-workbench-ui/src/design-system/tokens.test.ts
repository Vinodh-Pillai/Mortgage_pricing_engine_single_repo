import { describe, expect, it } from 'vitest';
import { breakpoints, colors, cssVariableMap, designTokens, motion, spacing, typography } from './tokens';

describe('DesignTokensTest', () => {
  it('resolvesColorScaleInDarkMode', () => {
    expect(colors.dark.background).toBe('#07111f');
    expect(colors.dark.primary).toBe('#2dd4bf');
    expect(cssVariableMap['--ds-color-background']).toBe(colors.dark.background);
  });

  it('resolvesColorScaleInLightMode', () => {
    expect(colors.light.background).toBe('#f6f9ff');
    expect(colors.light.primary).toBe('#0f766e');
    expect(colors.light.text).not.toBe(colors.dark.text);
  });

  it('exportsRequiredTokenFamilies', () => {
    expect(designTokens).toEqual(expect.objectContaining({ colors, spacing, typography, breakpoints, motion }));
    expect(spacing[4]).toBe('1rem');
    expect(breakpoints.desktop).toBe('1200px');
    expect(motion.duration.fast).toBe('120ms');
  });
});

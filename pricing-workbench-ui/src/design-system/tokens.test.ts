import { describe, expect, it } from 'vitest';
import { breakpoints, colors, cssVariableMap, designTokens, motion, roleColors, spacing, typography, validateDesignTokens } from './tokens';

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
    expect(motion.duration.fast).toBe('100ms');
    expect(motion.durations.slower).toBe('500ms');
    expect(motion.stagger.base).toBe('50ms');
    expect(motion.easings.spring).toContain('cubic-bezier');
  });

  it('exportsGlassAndRoleTokensForCssAndTypeScript', () => {
    expect(roleColors['loan-officer']).toEqual({ bg: '#2dd4bf', text: '#07111f', border: '#14b8a6' });
    expect(cssVariableMap['--ds-role-loan-officer-bg']).toBe(roleColors['loan-officer'].bg);
    expect(designTokens.glass.blurSubtle).toBe('blur(8px)');
  });

  it('validatesDesignTokenSchema', () => {
    expect(validateDesignTokens()).toEqual({ valid: true, errors: [] });
  });
});

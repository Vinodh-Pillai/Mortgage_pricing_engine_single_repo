export const WCAG_AA_TEXT_RATIO = 4.5;
export const WCAG_AA_UI_RATIO = 3;

export type ContrastTarget = 'text' | 'large-text' | 'ui';

type Rgb = { red: number; green: number; blue: number };

function expandHex(hex: string) {
  const clean = hex.replace('#', '').trim();
  if (clean.length === 3) {
    return clean.split('').map((part) => part + part).join('');
  }
  return clean;
}

export function hexToRgb(hex: string): Rgb {
  const expanded = expandHex(hex);
  if (!/^[\da-f]{6}$/i.test(expanded)) {
    throw new Error(`Unsupported color format: ${hex}`);
  }

  return {
    red: Number.parseInt(expanded.slice(0, 2), 16),
    green: Number.parseInt(expanded.slice(2, 4), 16),
    blue: Number.parseInt(expanded.slice(4, 6), 16),
  };
}

function channelLuminance(channel: number) {
  const normalized = channel / 255;
  return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
}

export function relativeLuminance(color: string) {
  const { red, green, blue } = hexToRgb(color);
  return 0.2126 * channelLuminance(red) + 0.7152 * channelLuminance(green) + 0.0722 * channelLuminance(blue);
}

export function contrastRatio(colorA: string, colorB: string) {
  const lighter = Math.max(relativeLuminance(colorA), relativeLuminance(colorB));
  const darker = Math.min(relativeLuminance(colorA), relativeLuminance(colorB));
  return Number(((lighter + 0.05) / (darker + 0.05)).toFixed(2));
}

export function requiredContrastRatio(target: ContrastTarget) {
  return target === 'text' ? WCAG_AA_TEXT_RATIO : WCAG_AA_UI_RATIO;
}

export function meetsContrastRatio(colorA: string, colorB: string, target: ContrastTarget = 'text') {
  return contrastRatio(colorA, colorB) >= requiredContrastRatio(target);
}

export function validateContrastPair(colorA: string, colorB: string, target: ContrastTarget = 'text') {
  const ratio = contrastRatio(colorA, colorB);
  const requiredRatio = requiredContrastRatio(target);
  return {
    colorA,
    colorB,
    target,
    ratio,
    requiredRatio,
    passes: ratio >= requiredRatio,
  };
}

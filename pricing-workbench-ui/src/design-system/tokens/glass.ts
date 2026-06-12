export const glass = {
  background: 'rgba(16, 31, 56, 0.7)',
  backgroundStrong: 'rgba(16, 31, 56, 0.85)',
  backgroundLight: 'rgba(255, 255, 255, 0.72)',
  surface: 'rgba(23, 42, 70, 0.72)',
  surfaceLight: 'rgba(255, 255, 255, 0.72)',
  border: 'rgba(54, 81, 120, 0.5)',
  borderStrong: 'rgba(116, 185, 255, 0.3)',
  shadow: '0 8px 32px rgba(0, 0, 0, 0.3)',
  shadowStrong: '0 16px 48px rgba(0, 0, 0, 0.4)',
  shadowColored: '0 8px 32px rgba(45, 212, 191, 0.15)',
  shadowRaised: '0 24px 70px rgba(0, 0, 0, 0.34)',
  blur: 'blur(16px)',
  blurStrong: 'blur(24px)',
  blurSubtle: 'blur(8px)',
} as const;

export type GlassToken = keyof typeof glass;

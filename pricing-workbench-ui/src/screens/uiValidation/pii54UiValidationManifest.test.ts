import { describe, expect, it } from 'vitest';
import { pii54BlockedValidationGaps, pii54ValidationCommands, pii54ValidationScreens, validatePii54Manifest } from './pii54UiValidationManifest';

describe('PII-54 UI validation manifest', () => {
  it('covers PII-51, PII-52, and PII-53 screens with focused local gates', () => {
    expect(validatePii54Manifest()).toEqual([]);
    expect(pii54ValidationScreens.map((screen) => screen.storyId)).toEqual(['PII-51-S01', 'PII-52-S01', 'PII-53-S01']);
    expect(pii54ValidationScreens.map((screen) => screen.route)).toEqual(['/home', '/admin/tenants', '/admin/products']);
  });

  it('records required command families without external endpoints, tokens, or mortgage pricing constants', () => {
    expect(pii54ValidationCommands).toEqual(expect.arrayContaining([
      'npm run test:ui-pii54',
      'npm run test:coverage',
      'npm run storybook:build',
      'npm run test:e2e',
      'npm run test:a11y',
      'npm run lighthouse:ci',
    ]));
    const serialized = JSON.stringify({ pii54ValidationCommands, pii54BlockedValidationGaps });
    expect(serialized).not.toMatch(/CHROMATIC_TOKEN|password|secret|rate table|fee amount|eligibility threshold/i);
  });
});

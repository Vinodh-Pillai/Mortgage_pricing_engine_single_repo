import '@testing-library/jest-dom/vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { lazy, type ReactElement } from 'react';
import { createScreenModule, getEvidenceTarget, type ScreenModuleRegistration } from './ScreenModule';
import { clearScreenRegistryForTests, getAllScreenModules, registerScreenModule, resolveScreenModule } from './registry';
import { lazyScreen, preloadOnIntent } from './lazy';
import { ScreenWrapper } from './VisualState';
import type { ScreenProps } from './ScreenProps';

const TestComponent = lazy(async () => ({ default: () => <div>Ready content</div> }));

function BrokenScreen(): ReactElement {
  throw new Error('Screen wrapper render failure');
}

function moduleFixture(overrides: Partial<ScreenModuleRegistration> = {}) {
  return createScreenModule({
    id: 'quote-offers',
    label: 'Offer comparison',
    routePattern: '/quote/:runId/offers',
    breadcrumb: 'Offers',
    screenPackage: 'screens/quoteOffers',
    dataBoundary: 'lib/api/offers',
    stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
    evidenceTarget: '.local-harness/evidence/PII-24-S03/quote-offers.json',
    Component: TestComponent,
    ...overrides,
  });
}

describe('screen module contract', () => {
  afterEach(() => {
    cleanup();
    clearScreenRegistryForTests();
  });

  it('ScreenContractTest.validModulePassesValidation', () => {
    const module = moduleFixture();
    expect(module.match('/quote/run-123/offers')).toBe(true);
    expect(module.evidenceTarget).toBe('.local-harness/evidence/PII-24-S03/quote-offers.json');
  });

  it('ScreenContractTest.missingRequiredFieldThrows', () => {
    expect(() => moduleFixture({ label: '' })).toThrow(/label is required/);
  });

  it('ScreenContractTest.invalidRoutePatternThrows', () => {
    expect(() => moduleFixture({ routePattern: 'quote/:runId/offers' })).toThrow(/Invalid route pattern|routePattern must start/);
    expect(() => moduleFixture({ routePattern: '/quote/:/offers' })).toThrow(/Invalid route pattern|invalid parameter/);
  });

  it('ScreenContractTest.evidenceTargetFollowsConvention', () => {
    expect(getEvidenceTarget('quote-offers')).toBe('.local-harness/evidence/PII-24-S03/quote-offers.json');
    expect(() => moduleFixture({ evidenceTarget: '/tmp/quote-offers.json' })).toThrow(/evidenceTarget/);
  });

  it('RegistryTest.resolvesExactMatch and parametric match', () => {
    const module = registerScreenModule(moduleFixture());
    expect(resolveScreenModule('/quote/run-123/offers')).toBe(module);
    expect(resolveScreenModule('/quote/run-123/offers/')).toBe(module);
  });

  it('RegistryTest.fallsBackTo404', () => {
    registerScreenModule(moduleFixture());
    expect(resolveScreenModule('/unknown').id).toBe('not-found');
  });

  it('RegistryTest.filtersByPersona', () => {
    registerScreenModule(moduleFixture({ personaVisibility: ['pricing analyst'] }));
    registerScreenModule(moduleFixture({ id: 'ops-cases', routePattern: '/ops/cases', evidenceTarget: '.local-harness/evidence/PII-24-S03/ops-cases.json', personaVisibility: ['operations lead'] }));
    expect(getAllScreenModules('pricing analyst').map((module) => module.id)).toEqual(['quote-offers']);
  });

  it('LazyLoadTest.preloadsOnHover', async () => {
    const importFn = vi.fn(async () => ({ default: (_props: ScreenProps) => <div>Lazy screen</div> }));
    const lazyModule = lazyScreen(importFn);
    await lazyModule.preload();
    preloadOnIntent(lazyModule.preload).onMouseEnter();
    expect(importFn).toHaveBeenCalledTimes(1);
  });

  it('VisualStateTest.renderSkeletonForLoading', () => {
    render(<ScreenWrapper screenId="quote-offers" title="Offer comparison" state="loading" />);
    expect(screen.getByRole('status', { name: 'Loading screen module' })).toBeInTheDocument();
  });

  it('VisualStateTest.renderBlockedWithRemediation', () => {
    render(<ScreenWrapper screenId="quote-offers" title="Offer comparison" state="blocked" dependencyStatus="Configured service is unavailable." remediation={['Connect service contract.']} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Configured service is unavailable.');
    expect(screen.getByText('Connect service contract.')).toBeInTheDocument();
  });

  it('VisualStateTest.renderNeedsAttentionWithGuidance', () => {
    render(<ScreenWrapper screenId="quote-offers" title="Offer comparison" state="needs-attention" guidance={['Review setup evidence.']} />);
    expect(screen.getByRole('status')).toHaveTextContent('Review setup guidance');
    expect(screen.getByText('Review setup evidence.')).toBeInTheDocument();
  });

  it('VisualStateTest.readyRenderErrorShowsScreenFallback', () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    render(
      <ScreenWrapper screenId="quote-offers" title="Offer comparison" state="ready" uiTraceId="trace-wrapper-error">
        <BrokenScreen />
      </ScreenWrapper>,
    );

    expect(screen.getByRole('alert')).toHaveTextContent('quote-offers is temporarily unavailable');
    expect(screen.getByText('trace-wrapper-error')).toBeInTheDocument();
  });
});

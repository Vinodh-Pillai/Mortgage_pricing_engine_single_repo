import { render, type RenderResult } from '@testing-library/react';
import type { ReactElement } from 'react';

export type A11yDependencyStatus = 'available' | 'dependency_unavailable';

export function axeDependencyStatus(): A11yDependencyStatus {
  return 'dependency_unavailable';
}

export function axeTest(component: ReactElement): RenderResult & { a11yStatus: A11yDependencyStatus; blockedReason: string } {
  return {
    ...render(component),
    a11yStatus: axeDependencyStatus(),
    blockedReason: 'axe-core/jest-axe are not declared in pricing-workbench-ui package dependencies.',
  };
}

export function a11ySnapshot(componentName: string, notes: string[] = []) {
  return {
    componentName,
    status: 'manual_review_required' as const,
    blockers: ['Storybook a11y addon and axe runtime are not declared in pricing-workbench-ui package dependencies.'],
    notes,
  };
}

import type React from 'react';
import type { ScreenProps, ScreenVisualState, ValidationResult } from './ScreenProps';

export const requiredVisualStates: ScreenVisualState[] = ['loading', 'empty', 'blocked', 'needs-attention', 'ready'];

export interface ScreenModule {
  id: string;
  label: string;
  routePattern: string;
  breadcrumb: string;
  screenPackage: string;
  dataBoundary: string;
  stateCoverage: ScreenVisualState[];
  personaVisibility?: string[];
  dependencyStatus?: string;
  adapterStatus?: string;
  evidenceTarget: string;
  match: (pathname: string) => boolean;
  Component: React.LazyExoticComponent<React.ComponentType<ScreenProps>>;
  validate?: (props: ScreenProps) => ValidationResult;
  preload?: () => Promise<unknown>;
}

export type ScreenModuleRegistration = Omit<ScreenModule, 'match'> & {
  match?: (pathname: string) => boolean;
};

const kebabCasePattern = /^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$/;
const evidenceTargetPattern = /^\.local-harness\/evidence\/[^/]+\/[a-z][a-z0-9-]*\.json$/;

export function createScreenModule(module: ScreenModuleRegistration): ScreenModule {
  const nextModule: ScreenModule = {
    ...module,
    match: module.match ?? createRouteMatcher(module.routePattern),
  };
  validateScreenModule(nextModule);
  return nextModule;
}

export function validateScreenModule(module: ScreenModule): ValidationResult {
  const blockers: string[] = [];

  if (!module.id || !kebabCasePattern.test(module.id)) blockers.push('id must be a non-empty kebab-case value.');
  if (!module.label?.trim()) blockers.push('label is required.');
  if (!module.routePattern?.startsWith('/')) blockers.push('routePattern must start with /.');
  if (!isRoutePatternValid(module.routePattern)) blockers.push('routePattern contains an invalid parameter segment.');
  if (!module.breadcrumb?.trim()) blockers.push('breadcrumb is required.');
  if (!module.screenPackage?.trim()) blockers.push('screenPackage is required.');
  if (!module.dataBoundary?.trim()) blockers.push('dataBoundary is required.');
  if (!Array.isArray(module.stateCoverage) || module.stateCoverage.length === 0) blockers.push('stateCoverage is required.');
  const missingStates = requiredVisualStates.filter((state) => !module.stateCoverage.includes(state));
  if (missingStates.length > 0) blockers.push(`stateCoverage missing required states: ${missingStates.join(', ')}.`);
  if (!evidenceTargetPattern.test(module.evidenceTarget)) blockers.push('evidenceTarget must follow .local-harness/evidence/<story_id>/<screen>.json.');
  if (typeof module.match !== 'function') blockers.push('match function is required.');
  if (!module.Component || typeof module.Component !== 'object') blockers.push('Component must be a React.lazy component.');

  if (blockers.length > 0) {
    throw new Error(`Invalid screen module ${module.id || '<unknown>'}: ${blockers.join(' ')}`);
  }
  return { valid: true, blockers };
}

export function getEvidenceTarget(screenId: string, storyId = 'PII-24-S03') {
  if (!kebabCasePattern.test(screenId)) throw new Error('screenId must be kebab-case before evidence target generation.');
  return `.local-harness/evidence/${storyId}/${screenId}.json`;
}

export function createRouteMatcher(routePattern: string) {
  if (!isRoutePatternValid(routePattern)) throw new Error(`Invalid route pattern: ${routePattern}`);
  const expression = routePattern
    .replace(/\/$/, '')
    .split('/')
    .map((segment) => {
      if (!segment) return '';
      if (segment.startsWith(':')) return '[^/]+';
      return segment.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
    })
    .join('/');
  const matcher = new RegExp(`^${expression}/?$`);
  return (pathname: string) => matcher.test(pathname.replace(/\/$/, '') || '/');
}

function isRoutePatternValid(routePattern: string) {
  if (!routePattern.startsWith('/')) return false;
  return routePattern.split('/').every((segment) => !segment.startsWith(':') || /^:[A-Za-z][A-Za-z0-9_]*$/.test(segment));
}

import { lazy } from 'react';
import { NotFoundScreen } from '../NotFoundScreen';
import { createScreenModule, getEvidenceTarget, validateScreenModule, type ScreenModule } from './ScreenModule';

const registry = new Map<string, ScreenModule>();

export const notFoundScreenModule = createScreenModule({
  id: 'not-found',
  label: 'Route not found',
  routePattern: '/not-found',
  breadcrumb: 'Not Found',
  screenPackage: 'screens/NotFoundScreen',
  dataBoundary: 'screen-registry',
  stateCoverage: ['loading', 'empty', 'blocked', 'needs-attention', 'ready'],
  dependencyStatus: 'No registered screen module matched the requested path.',
  adapterStatus: 'Registry fallback is local to the pricing workbench UI.',
  evidenceTarget: getEvidenceTarget('not-found'),
  match: () => false,
  Component: lazy(async () => ({ default: NotFoundScreen })),
});

export function registerScreenModule(module: ScreenModule) {
  validateScreenModule(module);
  const existing = registry.get(module.id);
  if (existing) return handleRegistryProblem(`Screen module id already registered: ${module.id}`, existing);
  const routeConflict = Array.from(registry.values()).find((registered) => registered.routePattern === module.routePattern);
  if (routeConflict) return handleRegistryProblem(`Screen module route already registered: ${module.routePattern}`, routeConflict);
  registry.set(module.id, module);
  return module;
}

export function resolveScreenModule(pathname: string): ScreenModule {
  return Array.from(registry.values()).find((module) => module.match(pathname)) ?? notFoundScreenModule;
}

export function getAllScreenModules(persona?: string): ScreenModule[] {
  const modules = Array.from(registry.values());
  if (!persona) return modules;
  return modules.filter((module) => !module.personaVisibility?.length || module.personaVisibility.includes(persona));
}

export { getEvidenceTarget };

export function clearScreenRegistryForTests() {
  registry.clear();
}

function handleRegistryProblem(message: string, existing: ScreenModule): ScreenModule {
  if (import.meta.env?.DEV === false) {
    console.warn(message);
    return existing;
  }
  throw new Error(message);
}

export type Pii54ValidationScreen = {
  storyId: 'PII-51-S01' | 'PII-52-S01' | 'PII-53-S01';
  screenId: 'tenant-home' | 'tenant-admin' | 'product-admin';
  route: string;
  componentTestCommand: string;
  storybookFile: string;
  e2eAssertions: string[];
  accessibilityGate: string;
};

export const pii54ValidationScreens: Pii54ValidationScreen[] = [
  {
    storyId: 'PII-51-S01',
    screenId: 'tenant-home',
    route: '/home',
    componentTestCommand: 'npm run test:tenant-home',
    storybookFile: 'src/screens/tenantHome/TenantHomeScreen.stories.tsx',
    e2eAssertions: ['route registered in shell navigation', 'local authentication gate visible', 'component behavior covered by Vitest'],
    accessibilityGate: 'tests/e2e/a11y/pii54-screens.a11y.test.ts',
  },
  {
    storyId: 'PII-52-S01',
    screenId: 'tenant-admin',
    route: '/admin/tenants',
    componentTestCommand: 'npm run test:tenant-admin',
    storybookFile: 'src/screens/tenantAdmin/TenantAdminScreen.stories.tsx',
    e2eAssertions: ['route registered in shell navigation', 'local authentication gate visible', 'component behavior covered by Vitest'],
    accessibilityGate: 'tests/e2e/a11y/pii54-screens.a11y.test.ts',
  },
  {
    storyId: 'PII-53-S01',
    screenId: 'product-admin',
    route: '/admin/products',
    componentTestCommand: 'npm run test:product-admin',
    storybookFile: 'src/screens/productAdmin/ProductAdminScreen.stories.tsx',
    e2eAssertions: ['route registered in shell navigation', 'local authentication gate visible', 'component behavior covered by Vitest'],
    accessibilityGate: 'tests/e2e/a11y/pii54-screens.a11y.test.ts',
  },
];

export const pii54ValidationCommands = [
  'npm run test:ui-pii54',
  'npm run test:coverage',
  'npm run storybook:build',
  'npm run test:visual',
  'npm run test:e2e',
  'npm run test:a11y',
  'npm run lighthouse:ci',
] as const;

export const pii54BlockedValidationGaps = [
  'Storybook visual validation is represented by local stories and a storybook:build script using declared Storybook packages; external Chromatic upload is intentionally not required without a token.',
  'Playwright and axe gates validate route registration and the local authentication gate; authenticated full-screen journeys remain blocked until the local auth service/session runtime is available.',
  'Lighthouse CI is represented by a deterministic local budget validator; live browser LHCI remains optional when local runtime ports or service dependencies are unavailable.',
] as const;

export function validatePii54Manifest() {
  const problems: string[] = [];
  const screenIds = new Set<string>();
  for (const screen of pii54ValidationScreens) {
    if (screenIds.has(screen.screenId)) problems.push(`duplicate screen id ${screen.screenId}`);
    screenIds.add(screen.screenId);
    if (!screen.route.startsWith('/')) problems.push(`${screen.screenId} route must be absolute app route`);
    if (!screen.componentTestCommand.startsWith('npm run test:')) problems.push(`${screen.screenId} must use package test script`);
    if (!screen.storybookFile.endsWith('.stories.tsx')) problems.push(`${screen.screenId} must have a TSX story file`);
    if (screen.e2eAssertions.length < 3) problems.push(`${screen.screenId} needs at least three E2E assertions`);
  }
  return problems;
}

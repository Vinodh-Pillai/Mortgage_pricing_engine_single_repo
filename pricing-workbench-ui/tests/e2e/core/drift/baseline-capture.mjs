import { chromium } from 'playwright';
import { apiHelper } from '../helpers/api-helper.js';
import { uiHelper } from '../helpers/ui-helper.js';
import { personas, PersonaRole } from '../personas/personas.js';
import { testScenarios } from '../fixtures/test-data.js';
import { DriftDetector, BaselineExpectation } from './drift-detector.js';
import fs from 'fs';
import path from 'path';

const BASELINES_DIR = './tests/baselines';
const BASE_URL = process.env.BASE_URL || 'http://localhost:3000';
const API_BASE = process.env.VITE_API_BASE || 'http://localhost:8080';

async function captureBaselines() {
  console.log('[Baseline Capture] Starting baseline capture...');
  
  const browser = await chromium.launch({ headless: true });
  const context = await browser.newContext({ viewport: { width: 1920, height: 1080 } });
  const page = await context.newPage();
  
  const detector = new DriftDetector();
  const allBaselines: BaselineExpectation[] = [];

  try {
    // Initialize API helper
    apiHelper.init();
    
    // Test each persona
    for (const [personaRole, persona] of Object.entries(personas)) {
      console.log(`\n[Baseline Capture] Capturing for persona: ${personaRole}`);
      
      const testCtx = apiHelper.createContext(personaRole as PersonaRole);
      
      // Test accessible routes
      for (const route of persona.accessibleRoutes.slice(0, 3)) {
        const routeBaseline = await captureRouteBaseline(
          detector, page, testCtx, uiHelper(page), personaRole, route
        );
        if (routeBaseline) allBaselines.push(routeBaseline);
      }

      // Test API endpoints
      const apiBaselines = await captureApiBaselines(testCtx, personaRole);
      allBaselines.push(...apiBaselines);
    }

    // Save baselines
    await saveBaselines(allBaselines);
    
    console.log('\n[Baseline Capture] Complete! Baselines saved to', BASELINES_DIR);
    
  } catch (error) {
    console.error('[Baseline Capture] Error:', error);
    throw error;
  } finally {
    await context.close();
    await browser.close();
    await apiHelper.dispose();
  }
}

async function captureRouteBaseline(
  detector: DriftDetector,
  page: any,
  testCtx: any,
  ui: any,
  personaRole: string,
  route: string
): Promise<BaselineExpectation | null> {
  const startTime = Date.now();
  
  try {
    const result = await ui.navigateTo(route, { waitForLoad: true, timeout: 30000 });
    const loadTime = Date.now() - startTime;
    
    if (!result.success) {
      console.log(`  [WARN] Route ${route} failed: ${result.errors.join(', ')}`);
      return null;
    }

    // Capture UI expectations
    const elements = await page.locator('[data-testid], [data-offer-id], [role], [aria-label]').all();
    const expectedElements = [];
    for (const el of elements.slice(0, 50)) {
      const testId = await el.getAttribute('data-testid');
      const offerId = await el.getAttribute('data-offer-id');
      const role = await el.getAttribute('role');
      const ariaLabel = await el.getAttribute('aria-label');
      if (testId) expectedElements.push(`data-testid="${testId}"`);
      else if (offerId) expectedElements.push(`data-offer-id="${offerId}"`);
      else if (role) expectedElements.push(`role="${role}"`);
      else if (ariaLabel) expectedElements.push(`aria-label="${ariaLabel}"`);
    }

    // Accessibility check
    let accessibilityScore = 100;
    try {
      await ui.verifyAccessibility();
    } catch {
      accessibilityScore = 50;
    }

    const baseline: BaselineExpectation = {
      scenario: `route-${route.replace(/[^a-zA-Z0-9]/g, '-')}`,
      persona: personaRole,
      version: '1.0.0',
      capturedAt: new Date().toISOString(),
      expectations: {
        apiResponses: {},
        uiStates: {
          [route]: {
            route,
            expectedElements: [...new Set(expectedElements)],
            forbiddenElements: [],
            loadTimeMs: { max: loadTime * 2 },
            accessibilityScore,
          },
        },
        pricingOutcomes: [],
        eligibilityDecisions: [],
        marginCalculations: [],
        latencyBudgets: [],
      },
    };

    console.log(`  [OK] Captured ${route} (${loadTime}ms)`);
    return baseline;
    
  } catch (error) {
    console.log(`  [ERROR] Failed to capture ${route}:`, error);
    return null;
  }
}

async function captureApiBaselines(testCtx: any, personaRole: string): Promise<BaselineExpectation[]> {
  const baselines: BaselineExpectation[] = [];
  const endpoints = [
    { path: `/api/v1/tenants/${testCtx.tenantId}/quote-runs/intake-metadata`, name: 'intake-metadata' },
    { path: '/api/v1/ops/rate-feeds', name: 'rate-feeds' },
    { path: '/api/v1/ops/performance', name: 'performance' },
    { path: '/api/v1/ops/cases', name: 'ops-cases' },
    { path: '/api/v1/adjustments/evidence', name: 'adjustments', params: { tenantContext: testCtx.tenantId } },
    { path: '/api/v1/margins/profitability', name: 'margins', params: { tenantContext: testCtx.tenantId } },
    { path: '/api/v1/exceptions/concessions/workbench', name: 'exceptions', params: { tenantContext: testCtx.tenantId } },
    { path: '/api/v1/admin/governance', name: 'governance' },
    { path: '/api/v1/products/catalog/manager', name: 'catalog' },
    { path: '/api/v1/compliance/evidence', name: 'compliance' },
    { path: '/api/v1/ml-advisory/insights', name: 'ml-advisory' },
  ];

  for (const endpoint of endpoints) {
    try {
      const startTime = Date.now();
      const response = await apiHelper.get(testCtx, endpoint.path, endpoint.params);
      const responseTime = Date.now() - startTime;

      const apiBaseline: BaselineExpectation = {
        scenario: `api-${endpoint.name}`,
        persona: personaRole,
        version: '1.0.0',
        capturedAt: new Date().toISOString(),
        expectations: {
          apiResponses: {
            [endpoint.path]: {
              endpoint: endpoint.path,
              expectedStatus: response.status,
              expectedFields: Object.keys(response.data || {}),
              fieldConstraints: buildFieldConstraints(response.data),
              responseTimeMs: { max: responseTime * 2, p95: responseTime * 3 },
            },
          },
          uiStates: {},
          pricingOutcomes: [],
          eligibilityDecisions: [],
          marginCalculations: [],
          latencyBudgets: [{
            operation: endpoint.name,
            p50Ms: responseTime,
            p95Ms: responseTime * 1.5,
            p99Ms: responseTime * 2,
          }],
        },
      };

      baselines.push(apiBaseline);
      console.log(`  [API] ${endpoint.name}: ${response.status} (${responseTime}ms)`);
      
    } catch (error) {
      console.log(`  [API] ${endpoint.name} failed:`, error);
    }
  }

  return baselines;
}

function buildFieldConstraints(data: any): Record<string, any> {
  const constraints: Record<string, any> = {};
  
  function traverse(obj: any, prefix = '') {
    if (!obj || typeof obj !== 'object') return;
    
    for (const [key, value] of Object.entries(obj)) {
      const fullKey = prefix ? `${prefix}.${key}` : key;
      
      if (Array.isArray(value)) {
        constraints[fullKey] = { type: 'array', required: true };
        if (value.length > 0) {
          traverse(value[0], fullKey);
        }
      } else if (value && typeof value === 'object') {
        constraints[fullKey] = { type: 'object', required: true };
        traverse(value, fullKey);
      } else {
        const type = typeof value;
        constraints[fullKey] = { type, required: true };
        if (type === 'number') {
          constraints[fullKey].min = value * 0.5;
          constraints[fullKey].max = value * 1.5;
        }
      }
    }
  }
  
  traverse(data);
  return constraints;
}

async function saveBaselines(baselines: BaselineExpectation[]) {
  if (!fs.existsSync(BASELINES_DIR)) {
    fs.mkdirSync(BASELINES_DIR, { recursive: true });
  }

  for (const baseline of baselines) {
    const filename = `${baseline.scenario}-${baseline.persona}.json`;
    const filepath = path.join(BASELINES_DIR, filename);
    fs.writeFileSync(filepath, JSON.stringify(baseline, null, 2));
  }

  // Also create a manifest
  const manifest = {
    version: '1.0.0',
    capturedAt: new Date().toISOString(),
    baselineCount: baselines.length,
    scenarios: [...new Set(baselines.map(b => b.scenario))],
    personas: [...new Set(baselines.map(b => b.persona))],
  };
  
  fs.writeFileSync(
    path.join(BASELINES_DIR, 'manifest.json'),
    JSON.stringify(manifest, null, 2)
  );

  console.log(`\n[Baseline Capture] Saved ${baselines.length} baselines to ${BASELINES_DIR}`);
}

captureBaselines().catch(console.error);
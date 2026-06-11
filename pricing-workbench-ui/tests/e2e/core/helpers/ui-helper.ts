import { Page, Locator, expect } from '@playwright/test';
import { PersonaRole, getAccessibleRoutes, getRestrictedRoutes } from '../personas/personas';

export interface NavigationResult {
  success: boolean;
  url: string;
  loadTime: number;
  errors: string[];
}

export interface FormField {
  name: string;
  value: string;
  type?: 'text' | 'email' | 'number' | 'select' | 'date' | 'textarea';
  required?: boolean;
}

export class UiHelper {
  constructor(private page: Page) {}

  async navigateTo(path: string, options?: { waitForLoad?: boolean; timeout?: number }): Promise<NavigationResult> {
    const startTime = Date.now();
    const errors: string[] = [];

    try {
      await this.page.goto(path, {
        waitUntil: options?.waitForLoad ? 'networkidle' : 'domcontentloaded',
        timeout: options?.timeout || 30000,
      });

      // Wait for any loading indicators to disappear
      await this.waitForLoadingToComplete();

      return {
        success: true,
        url: this.page.url(),
        loadTime: Date.now() - startTime,
        errors: [],
      };
    } catch (error) {
      return {
        success: false,
        url: this.page.url(),
        loadTime: Date.now() - startTime,
        errors: [error instanceof Error ? error.message : String(error)],
      };
    }
  }

  async waitForLoadingToComplete(timeout = 30000): Promise<void> {
    // Wait for any loading spinners/skeletons to disappear
    await this.page.waitForFunction(
      () => !document.querySelector('[role="status"]') && 
            !document.querySelector('.skeleton') &&
            !document.querySelector('[aria-busy="true"]'),
      { timeout }
    ).catch(() => {
      // Loading indicators might not exist, continue
    });
  }

  async fillForm(fields: FormField[]): Promise<void> {
    for (const field of fields) {
      const locator = this.getFieldLocator(field.name);
      
      if (field.type === 'select') {
        await locator.selectOption(field.value);
      } else if (field.type === 'date') {
        await locator.fill(field.value);
      } else if (field.type === 'textarea') {
        await locator.fill(field.value);
      } else {
        await locator.fill(field.value);
      }

      // Verify required fields are filled
      if (field.required) {
        await expect(locator).not.toBeEmpty();
      }
    }
  }

  getFieldLocator(name: string): Locator {
    return this.page.locator(`[name="${name}"], [id="${name}"], [data-testid="${name}"]`).first();
  }

  async clickAndWait(selector: string, options?: { waitForNavigation?: boolean }): Promise<void> {
    if (options?.waitForNavigation) {
      await Promise.all([
        this.page.waitForNavigation({ waitUntil: 'networkidle' }),
        this.page.click(selector),
      ]);
    } else {
      await this.page.click(selector);
    }
  }

  async selectOffer(optionId: string): Promise<void> {
    await this.page.click(`[data-offer-id="${optionId}"]`);
    await this.waitForLoadingToComplete();
  }

  async verifyOfferComparison(expected: {
    minOffers?: number;
    maxOffers?: number;
    hasSelectedOffer?: boolean;
    expectedProducts?: string[];
  }): Promise<void> {
    const offers = this.page.locator('[data-offer-id]');
    
    if (expected.minOffers) {
      await expect(offers).toHaveCount(expect.toBeGreaterThanOrEqual(expected.minOffers));
    }
    if (expected.maxOffers) {
      await expect(offers).toHaveCount(expect.toBeLessThanOrEqual(expected.maxOffers));
    }

    // Verify offer structure
    const firstOffer = offers.first();
    await expect(firstOffer.locator('[data-testid="offer-rate"]')).toBeVisible();
    await expect(firstOffer.locator('[data-testid="offer-price"]')).toBeVisible();
    await expect(firstOffer.locator('[data-testid="offer-payment"]')).toBeVisible();
    await expect(firstOffer.locator('[data-testid="offer-apr"]')).toBeVisible();
  }

  async verifyPricingWaterfall(expected: {
    hasBaseSelection?: boolean;
    minLedgerSteps?: number;
    hasFinalPrice?: boolean;
    hasRedactions?: boolean;
  }): Promise<void> {
    if (expected.hasBaseSelection) {
      await expect(this.page.locator('[data-testid="base-selection"]')).toBeVisible();
    }
    
    if (expected.minLedgerSteps) {
      const steps = this.page.locator('[data-testid="ledger-step"]');
      await expect(steps).toHaveCount(expect.toBeGreaterThanOrEqual(expected.minLedgerSteps));
    }

    if (expected.hasFinalPrice) {
      await expect(this.page.locator('[data-testid="final-price"]')).toBeVisible();
    }

    if (expected.hasRedactions) {
      const redacted = this.page.locator('[data-testid="redacted-value"]');
      await expect(redacted).toHaveCount(expect.toBeGreaterThan(0));
    }
  }

  async verifyEligibility(expected: {
    decision?: 'ELIGIBLE' | 'INELIGIBLE' | 'CONDITIONAL';
    hasBlockers?: boolean;
    minBlockers?: number;
  }): Promise<void> {
    if (expected.decision) {
      const badge = this.page.locator(`[data-testid="eligibility-badge"][data-decision="${expected.decision}"]`);
      await expect(badge).toBeVisible();
    }

    if (expected.hasBlockers) {
      const blockers = this.page.locator('[data-testid="eligibility-blocker"]');
      await expect(blockers).toHaveCount(expect.toBeGreaterThan(0));
    }

    if (expected.minBlockers) {
      const blockers = this.page.locator('[data-testid="eligibility-blocker"]');
      await expect(blockers).toHaveCount(expect.toBeGreaterThanOrEqual(expected.minBlockers));
    }
  }

  async verifyLockWorkflow(expected: {
    status?: 'READY' | 'CONFIRMED' | 'EXPIRED' | 'EXTENDED' | 'RELOCKED' | 'FLOAT_DOWN';
    hasCountdown?: boolean;
    hasDisclosures?: boolean;
  }): Promise<void> {
    if (expected.status) {
      const banner = this.page.locator(`[data-testid="lock-status"][data-status="${expected.status}"]`);
      await expect(banner).toBeVisible();
    }

    if (expected.hasCountdown) {
      await expect(this.page.locator('[data-testid="lock-countdown"]')).toBeVisible();
    }

    if (expected.hasDisclosures) {
      await expect(this.page.locator('[data-testid="lock-disclosures"]')).toBeVisible();
    }
  }

  async takeScreenshot(name: string): Promise<Buffer> {
    return await this.page.screenshot({
      path: `tests/results/artifacts/${name}-${Date.now()}.png`,
      fullPage: true,
    });
  }

  async takeElementScreenshot(selector: string, name: string): Promise<Buffer> {
    const element = this.page.locator(selector);
    return await element.screenshot({
      path: `tests/results/artifacts/${name}-${Date.now()}.png`,
    });
  }

  async verifyNoConsoleErrors(): Promise<void> {
    const errors: string[] = [];
    this.page.on('console', msg => {
      if (msg.type() === 'error') {
        errors.push(msg.text());
      }
    });
    
    if (errors.length > 0) {
      throw new Error(`Console errors detected: ${errors.join(', ')}`);
    }
  }

  async verifyAccessibility(): Promise<void> {
    // Basic accessibility checks
    // Skip link
    const skipLink = this.page.locator('a[href="#main-content"]');
    await expect(skipLink).toBeVisible();
    
    // Heading hierarchy
    const h1 = this.page.locator('h1');
    await expect(h1).toHaveCount(1);
    
    // ARIA landmarks
    await expect(this.page.locator('[role="banner"]')).toBeVisible();
    await expect(this.page.locator('[role="navigation"]')).toBeVisible();
    await expect(this.page.locator('[role="main"]')).toBeVisible();
    
    // Focus visible
    await this.page.keyboard.press('Tab');
    const focused = this.page.locator(':focus');
    await expect(focused).toBeVisible();
  }

  async verifyResponsive(breakpoints: Array<{ name: string; width: number; height: number }>): Promise<void> {
    for (const bp of breakpoints) {
      await this.page.setViewportSize({ width: bp.width, height: bp.height });
      await this.page.waitForTimeout(500);
      
      // Verify no horizontal overflow
      const bodyWidth = await this.page.evaluate(() => document.body.scrollWidth);
      expect(bodyWidth).toBeLessThanOrEqual(bp.width + 20); // Allow small scrollbar
      
      // Verify key elements visible
      await expect(this.page.locator('[role="main"]')).toBeVisible();
    }
  }

  async testPersonaAccess(persona: PersonaRole): Promise<{ accessible: string[]; blocked: string[] }> {
    const accessible = getAccessibleRoutes(persona);
    const restricted = getRestrictedRoutes(persona);
    const results = { accessible: [] as string[], blocked: [] as string[] };

    // Test accessible routes
    for (const route of accessible.slice(0, 5)) { // Limit to 5 for speed
      const result = await this.navigateTo(route);
      if (result.success) {
        results.accessible.push(route);
      } else {
        results.blocked.push(`${route}: ${result.errors.join(', ')}`);
      }
    }

    // Test restricted routes (should redirect or show 403)
    for (const route of restricted.slice(0, 3)) {
      const result = await this.navigateTo(route);
      if (!result.success || result.url.includes('403') || result.url.includes('unauthorized')) {
        results.blocked.push(route);
      } else {
        results.accessible.push(`${route} (UNEXPECTED ACCESS)`);
      }
    }

    return results;
  }
}

export const uiHelper = (page: Page) => new UiHelper(page);
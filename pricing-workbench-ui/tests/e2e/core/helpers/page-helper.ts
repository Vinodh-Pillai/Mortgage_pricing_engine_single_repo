import { expect, type Page } from '@playwright/test';
import { loginAs } from './auth-helper';
import { mockPii25BackendApis } from './api-helper';

export async function openAsAdmin(page: Page, route: string): Promise<void> {
  await mockPii25BackendApis(page);
  await loginAs(page, 'admin');
  await page.goto(route, { waitUntil: 'domcontentloaded' });
}

export async function expectMajorFunctionalityPage(page: Page, title: string, requiredText: string[]): Promise<void> {
  await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await expect(page.locator('main.functionality-page')).toBeVisible();
  await expect(page.getByLabel(new RegExp(`${title} readiness metrics`, 'i'))).toBeVisible();
  for (const text of requiredText) await expect(page.getByText(text).first()).toBeVisible();
  await expect(page.getByLabel(new RegExp(`${title} records`, 'i')).or(page.getByRole('table')).first()).toBeVisible();
}

export async function exerciseTable(page: Page, filterValue: string, sortColumn: string): Promise<void> {
  await page.getByLabel(/filter records/i).fill(filterValue);
  await expect(page.getByText(filterValue, { exact: false }).first()).toBeVisible();
  await page.getByRole('button', { name: new RegExp(sortColumn, 'i') }).first().click();
  await expect(page.getByText(/Showing \d+ of \d+/)).toBeVisible();
}

export async function runBasicA11yKeyboardCheck(page: Page): Promise<void> {
  await page.keyboard.press('Tab');
  await expect(page.locator(':focus')).toBeVisible();
  await expect(page.locator('main').first()).toBeVisible();
}

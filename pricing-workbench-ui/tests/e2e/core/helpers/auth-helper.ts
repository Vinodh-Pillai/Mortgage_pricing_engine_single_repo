import { expect, type Page } from '@playwright/test';
import { getPii25Persona, type Pii25Persona, type Pii25PersonaRole } from '../personas/personas';

export const activePersonaStorageKey = 'wcpe:activePersona';

export async function loginAs(page: Page, persona: Pii25Persona | Pii25PersonaRole | string): Promise<Pii25Persona> {
  const resolved = typeof persona === 'string' ? getPii25Persona(persona) : persona;
  await page.addInitScript(([key, value]) => window.localStorage.setItem(key, value), [activePersonaStorageKey, resolved.id]);
  await page.evaluate(([key, value]) => window.localStorage.setItem(key, value), [activePersonaStorageKey, resolved.id]).catch(() => undefined);
  return resolved;
}

export async function loginThroughUi(page: Page, persona: Pii25Persona): Promise<void> {
  await page.goto('/login', { waitUntil: 'domcontentloaded' });
  await page.getByRole('searchbox', { name: /search personas/i }).fill(persona.name);
  await page.getByRole('button', { name: new RegExp(`select ${escapeRegex(persona.name)}`, 'i') }).click();
  await page.getByRole('button', { name: new RegExp(`continue as ${escapeRegex(persona.name)}`, 'i') }).click();
  await expect(page).toHaveURL(new RegExp(`${escapeForUrl(persona.defaultRoute)}$`));
  await expect.poll(() => page.evaluate((key) => window.localStorage.getItem(key), activePersonaStorageKey)).toBe(persona.id);
}

export async function logout(page: Page): Promise<void> {
  await page.evaluate((key) => window.localStorage.removeItem(key), activePersonaStorageKey);
}

function escapeRegex(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function escapeForUrl(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

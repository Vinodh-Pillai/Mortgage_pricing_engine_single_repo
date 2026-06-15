import { expect, test } from '@playwright/test';
import { buildSyntheticPersonaSeedSpecs, seedSyntheticPersonaUsers } from './synthetic-persona-seed';

test.describe('synthetic persona seed fixture', () => {
  test('builds admin, tenant admin, pricing admin, loan officer, and borrower usernames without passwords', () => {
    const specs = buildSyntheticPersonaSeedSpecs({ runId: 'PII-USER-SEED' });

    expect(specs.map((spec) => spec.persona)).toEqual(['admin', 'tenant-admin', 'pricing-admin', 'loan-officer', 'borrower']);
    expect(specs.map((spec) => spec.username)).toEqual([
      'admin.pii-user-seed@wcpe.synthetic.invalid',
      'tenant-admin.pii-user-seed@wcpe.synthetic.invalid',
      'pricing-admin.pii-user-seed@wcpe.synthetic.invalid',
      'loan-officer.pii-user-seed@wcpe.synthetic.invalid',
      'borrower.pii-user-seed@wcpe.synthetic.invalid',
    ]);
    expect(JSON.stringify(specs)).not.toMatch(/password/i);
  });

  test('posts to auth registration API and redacts generated passwords from receipts', async () => {
    const postedPayloads: unknown[] = [];
    const request = {
      async post(path: string, init: { data?: unknown }) {
        expect(path).toBe('/api/auth/register');
        postedPayloads.push(init.data);
        return {
          status: () => 201,
          json: async () => ({ user: { id: 'synthetic-user-id' } }),
        };
      },
    };

    const results = await seedSyntheticPersonaUsers({ request, runId: 'local-test' });

    expect(results).toHaveLength(5);
    expect(results.every((result) => result.status === 'created')).toBe(true);
    expect(results.every((result) => result.password === 'redacted')).toBe(true);
    expect(JSON.stringify(results)).not.toMatch(/Synthetic-[A-Za-z0-9-]+-Only!/);
    expect(postedPayloads).toHaveLength(5);
    expect(postedPayloads.every((payload) => typeof (payload as { password?: unknown }).password === 'string')).toBe(true);
  });

  test('records unavailable registration API without throwing or exposing passwords', async () => {
    const request = {
      async post() {
        return {
          status: () => 404,
          json: async () => ({ error: 'not found' }),
        };
      },
    };

    const results = await seedSyntheticPersonaUsers({ request, runId: 'local-test' });

    expect(results.every((result) => result.status === 'unavailable')).toBe(true);
    expect(JSON.stringify(results)).not.toMatch(/password":"(?!redacted)/i);
  });
});

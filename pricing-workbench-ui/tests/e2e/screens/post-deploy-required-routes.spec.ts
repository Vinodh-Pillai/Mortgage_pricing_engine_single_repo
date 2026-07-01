import { expect, test } from '@playwright/test';

type PersonaFixture = { id: string; email: string; fullName: string; role: string };

const personas: Record<string, PersonaFixture> = {
  loanOfficer: { id: 'persona-loan-officer', email: 'sarah.mitchell@loanweft.demo', fullName: 'Sarah Mitchell', role: 'loan_officer' },
  pricingAnalyst: { id: 'persona-pricing-analyst', email: 'david.chen@loanweft.demo', fullName: 'David Chen', role: 'pricing_analyst' },
  operationsLead: { id: 'persona-operations-lead', email: 'maria.rodriguez@loanweft.demo', fullName: 'Maria Rodriguez', role: 'operations_lead' },
  governanceReviewer: { id: 'persona-governance-reviewer', email: 'james.thompson@loanweft.demo', fullName: 'James Thompson', role: 'governance_reviewer' },
};

async function authenticateAs(page: import('@playwright/test').Page, persona: PersonaFixture) {
  await page.route('**/api/auth/me', async (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ user: persona }),
  }));
  await page.route('**/api/auth/logout', async (route) => route.fulfill({ status: 204 }));
}

test.describe('post-deploy required route availability', () => {
  const requiredRoutes = [
    { path: '/scenario-analysis', persona: personas.pricingAnalyst, heading: /Compare Offers|Offer comparison|Loading route/i },
    { path: '/rate-sheet-intake', persona: personas.pricingAnalyst, heading: /Rate Sheet Intake/i },
    { path: '/rate-feed-pipeline', persona: personas.operationsLead, heading: /Rate Feed/i },
    { path: '/tenant-admin', persona: personas.pricingAnalyst, heading: /Tenant Management/i },
    { path: '/governance', persona: personas.governanceReviewer, heading: /Governance/i },
    { path: '/margin-profitability', persona: personas.pricingAnalyst, heading: /Margin Profitability/i },
    { path: '/quickquote', persona: personas.loanOfficer, heading: /QuickQuote/i },
  ] as const;

  for (const routeCase of requiredRoutes) {
    test(`${routeCase.path} renders a usable page shell for its local/dev persona`, async ({ page }) => {
      await authenticateAs(page, routeCase.persona);
      await page.goto(routeCase.path);
      await expect(page.getByRole('heading', { name: routeCase.heading }).first()).toBeVisible();
      await expect(page.getByRole('heading', { name: /Access denied|Not found/i })).toHaveCount(0);
      await expect(page.getByText(/Your authenticated account does not have permission|Route unavailable/i)).toHaveCount(0);
    });
  }
});

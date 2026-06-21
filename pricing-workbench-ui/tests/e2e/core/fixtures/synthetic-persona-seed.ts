export type SyntheticPersonaKind = 'admin' | 'tenant-admin' | 'pricing-admin' | 'loan-officer' | 'borrower';

export type SyntheticApiRole = 'admin' | 'pricing_analyst' | 'loan_officer' | 'borrower';

export interface SyntheticPersonaSeedSpec {
  persona: SyntheticPersonaKind;
  username: string;
  fullName: string;
  apiRole: SyntheticApiRole;
  roleMappingNote: string;
  tenantContext: string;
}

export interface SyntheticPersonaSeedResult extends SyntheticPersonaSeedSpec {
  status: 'created' | 'already-exists' | 'unavailable' | 'failed';
  statusCode?: number;
  userId?: string;
  password: 'redacted';
  evidenceNote: string;
}

export interface SeedSyntheticPersonasOptions {
  request: PersonaSeedRequest;
  runId?: string;
  usernameDomain?: string;
  tenantContext?: string;
  authRegisterPath?: string;
}

type RegisterResponse = {
  status(): number;
  json(): Promise<unknown>;
};

export type PersonaSeedRequest = {
  post(path: string, init: { headers?: Record<string, string>; data?: unknown; failOnStatusCode?: boolean }): Promise<RegisterResponse>;
};

const defaultUsernameDomain = 'wcpe.synthetic.invalid';
const defaultTenantContext = 'ui-preview-tenant';

const seedDefinitions: Array<Omit<SyntheticPersonaSeedSpec, 'username' | 'tenantContext'>> = [
  {
    persona: 'admin',
    fullName: 'Synthetic Admin User',
    apiRole: 'admin',
    roleMappingNote: 'Uses the existing auth API admin role.',
  },
  {
    persona: 'tenant-admin',
    fullName: 'Synthetic Tenant Admin User',
    apiRole: 'admin',
    roleMappingNote: 'Mapped to admin because the current auth API does not expose a tenant_admin role.',
  },
  {
    persona: 'pricing-admin',
    fullName: 'Synthetic Pricing Admin User',
    apiRole: 'pricing_analyst',
    roleMappingNote: 'Mapped to pricing_analyst because the current auth API exposes pricing_analyst but not pricing_admin.',
  },
  {
    persona: 'loan-officer',
    fullName: 'Synthetic Sarah Mitchell Loan Officer',
    apiRole: 'loan_officer',
    roleMappingNote: 'Uses the existing auth API loan_officer role for the public Sarah Mitchell synthetic fixture persona.',
  },
  {
    persona: 'borrower',
    fullName: 'Synthetic Borrower User',
    apiRole: 'borrower',
    roleMappingNote: 'Uses the existing auth API borrower role.',
  },
];

export function buildSyntheticPersonaSeedSpecs(options: Omit<SeedSyntheticPersonasOptions, 'request'> = {}): SyntheticPersonaSeedSpec[] {
  const runId = sanitizeRunId(options.runId ?? 'local');
  const usernameDomain = sanitizeDomain(options.usernameDomain ?? defaultUsernameDomain);
  const tenantContext = options.tenantContext ?? defaultTenantContext;

  return seedDefinitions.map((definition) => ({
    ...definition,
    tenantContext,
    username: `${definition.persona}.${runId}@${usernameDomain}`,
  }));
}

export async function seedSyntheticPersonaUsers(options: SeedSyntheticPersonasOptions): Promise<SyntheticPersonaSeedResult[]> {
  const authRegisterPath = options.authRegisterPath ?? '/api/auth/register';
  const specs = buildSyntheticPersonaSeedSpecs(options);
  const results: SyntheticPersonaSeedResult[] = [];

  for (const spec of specs) {
    const password = generateSyntheticPassword();
    try {
      const response = await options.request.post(authRegisterPath, {
        headers: {
          'Content-Type': 'application/json',
          Accept: 'application/json',
          'X-Tenant-Context': spec.tenantContext,
          'X-Ui-Trace-Id': `synthetic-persona-seed-${spec.persona}`,
        },
        data: {
          email: spec.username,
          password,
          fullName: spec.fullName,
          role: spec.apiRole,
        },
        failOnStatusCode: false,
      }) as RegisterResponse;
      const statusCode = response.status();
      const data = await response.json().catch(() => ({}));
      results.push(toSeedResult(spec, statusCode, data));
    } catch (error) {
      results.push({
        ...spec,
        status: 'unavailable',
        password: 'redacted',
        evidenceNote: `Auth registration API unavailable for ${spec.persona}: ${error instanceof Error ? error.message : 'unknown error'}`,
      });
    }
  }

  return results;
}

function toSeedResult(spec: SyntheticPersonaSeedSpec, statusCode: number, data: unknown): SyntheticPersonaSeedResult {
  const userId = extractUserId(data);
  if (statusCode === 200 || statusCode === 201) {
    return { ...spec, status: 'created', statusCode, userId, password: 'redacted', evidenceNote: 'Created through /api/auth/register; password intentionally omitted from receipt.' };
  }
  if (statusCode === 409) {
    return { ...spec, status: 'already-exists', statusCode, userId, password: 'redacted', evidenceNote: 'User already exists; no password returned.' };
  }
  if (statusCode === 404 || statusCode === 501) {
    return { ...spec, status: 'unavailable', statusCode, userId, password: 'redacted', evidenceNote: 'Auth registration API is not available in this environment.' };
  }
  return { ...spec, status: 'failed', statusCode, userId, password: 'redacted', evidenceNote: `Auth registration API returned ${statusCode}; password intentionally omitted from receipt.` };
}

function extractUserId(data: unknown): string | undefined {
  if (!data || typeof data !== 'object') return undefined;
  const candidate = data as { user?: { id?: unknown }; id?: unknown };
  const id = candidate.user?.id ?? candidate.id;
  return typeof id === 'string' ? id : undefined;
}

function sanitizeRunId(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9-]+/g, '-').replace(/^-+|-+$/g, '').slice(0, 40) || 'local';
}

function sanitizeDomain(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9.-]+/g, '').replace(/^\.+|\.+$/g, '') || defaultUsernameDomain;
}

function generateSyntheticPassword(): string {
  const bytes = new Uint8Array(18);
  globalThis.crypto?.getRandomValues?.(bytes);
  const fallback = `${Date.now().toString(36)}${Math.random().toString(36).slice(2)}`;
  const token = Array.from(bytes, (byte) => byte.toString(36).padStart(2, '0')).join('').replace(/^0+$/, fallback).slice(0, 24);
  return `Synthetic-${token}-Only!`;
}

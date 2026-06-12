export const roleColors = {
  'loan-officer': { bg: '#2dd4bf', text: '#07111f', border: '#14b8a6' },
  'pricing-analyst': { bg: '#60a5fa', text: '#07111f', border: '#2563eb' },
  'operations-lead': { bg: '#fbbf24', text: '#07111f', border: '#b45309' },
  'governance-reviewer': { bg: '#a78bfa', text: '#07111f', border: '#7c3aed' },
  admin: { bg: '#f87171', text: '#ffffff', border: '#b91c1c' },
  'partner-manager': { bg: '#34d399', text: '#07111f', border: '#047857' },
  'compliance-officer': { bg: '#fb7185', text: '#ffffff', border: '#b91c1c' },
  borrower: { bg: '#818cf8', text: '#ffffff', border: '#4f46e5' },
} as const;

export type RoleColorKey = keyof typeof roleColors;
export type RoleColor = (typeof roleColors)[RoleColorKey];

const roleLabelToKey: Record<string, RoleColorKey> = {
  'loan officer': 'loan-officer',
  'loan-officer': 'loan-officer',
  'pricing analyst': 'pricing-analyst',
  'pricing-analyst': 'pricing-analyst',
  'operations lead': 'operations-lead',
  'operations-lead': 'operations-lead',
  'governance reviewer': 'governance-reviewer',
  'governance-reviewer': 'governance-reviewer',
  admin: 'admin',
  'partner manager': 'partner-manager',
  'partner-manager': 'partner-manager',
  'compliance officer': 'compliance-officer',
  'compliance-officer': 'compliance-officer',
  borrower: 'borrower',
};

export function roleColorKeyForLabel(role: string): RoleColorKey {
  return roleLabelToKey[role.trim().toLowerCase()] ?? 'pricing-analyst';
}

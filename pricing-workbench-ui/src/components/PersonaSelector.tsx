import { useMemo } from 'react';
import { Badge, Button, Select } from '../design-system';
import { useAuth } from '../lib/auth/AuthContext';
import { roleLabels, type Persona, type PersonaRole } from '../lib/auth/personas';

const roleOrder: PersonaRole[] = ['loan-officer', 'pricing-analyst', 'operations-lead', 'governance-reviewer', 'partner-manager', 'compliance-officer', 'borrower', 'admin'];

function groupByRole(personas: Persona[]) {
  return personas.reduce((groups, persona) => {
    groups[persona.role] = [...(groups[persona.role] ?? []), persona];
    return groups;
  }, {} as Partial<Record<PersonaRole, Persona[]>>);
}

export function PersonaSelector({ compact = false }: { compact?: boolean }) {
  const { currentPersona, availablePersonas, login, logout, isAuthenticated } = useAuth();
  const grouped = useMemo(() => groupByRole(availablePersonas), [availablePersonas]);

  if (!import.meta.env.DEV) return null;

  return (
    <section className={compact ? 'persona-selector persona-selector--compact' : 'persona-selector'} aria-label="Synthetic persona selector">
      <div className="persona-selector__current">
        <span className="persona-selector__label">Persona</span>
        {currentPersona ? (
          <>
            <strong>{currentPersona.name}</strong>
            <Badge>{roleLabels[currentPersona.role]}</Badge>
          </>
        ) : <span>Not signed in</span>}
      </div>

      <label className="persona-selector__control">
        <span className="persona-selector__label">Switch persona</span>
        <Select
          aria-label="Switch synthetic persona"
          value={currentPersona?.id ?? ''}
          onChange={(event) => { void login(event.target.value); }}
        >
          <option value="" disabled>Select a persona</option>
          {roleOrder.map((role) => grouped[role]?.length ? (
            <optgroup key={role} label={roleLabels[role]}>
              {grouped[role]?.map((persona) => (
                <option key={persona.id} value={persona.id}>{persona.name} — {persona.email}</option>
              ))}
            </optgroup>
          ) : null)}
        </Select>
      </label>

      {isAuthenticated ? <Button type="button" variant="ghost" size="sm" onClick={logout}>Log out</Button> : null}
    </section>
  );
}

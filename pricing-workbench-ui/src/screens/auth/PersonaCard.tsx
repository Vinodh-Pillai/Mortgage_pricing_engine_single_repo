import type { CSSProperties, KeyboardEvent } from 'react';
import { Chip } from '../../design-system';
import { roleLabels, type Persona, type PersonaRole } from '../../lib/auth/personas';

export const roleIcons: Record<PersonaRole, string> = {
  borrower: '🏠',
  'loan-officer': '👔',
  'pricing-analyst': '📊',
  'operations-lead': '⚙️',
  'governance-reviewer': '📋',
  admin: '🔧',
  'partner-manager': '🤝',
  'compliance-officer': '🛡️',
};

export interface PersonaCardProps {
  persona: Persona;
  isSelected: boolean;
  onSelect: () => void;
  style?: CSSProperties;
  showEmail?: boolean;
}

export function roleIconFor(role: PersonaRole) {
  return roleIcons[role];
}

export function visiblePermissionChips(persona: Persona, maxVisible = 3) {
  const visible = persona.permissions.slice(0, maxVisible);
  const overflow = Math.max(persona.permissions.length - visible.length, 0);
  return { visible, overflow };
}

export function PersonaCard({ persona, isSelected, onSelect, style, showEmail = true }: PersonaCardProps) {
  const permissions = visiblePermissionChips(persona);

  function handleKeyDown(event: KeyboardEvent<HTMLElement>) {
    if (event.key !== 'Enter' && event.key !== ' ') return;
    event.preventDefault();
    onSelect();
  }

  return (
    <article
      className={`persona-card${isSelected ? ' persona-card--selected' : ''}`}
      role="button"
      tabIndex={0}
      aria-pressed={isSelected}
      aria-label={`Select ${persona.name}, ${roleLabels[persona.role]}`}
      data-testid="persona-card"
      data-persona-id={persona.id}
      onClick={onSelect}
      onKeyDown={handleKeyDown}
      style={style}
    >
      <div className="persona-card__header">
        <span className="persona-card__icon" aria-hidden="true">{roleIconFor(persona.role)}</span>
        <div>
          <h3>{persona.name}</h3>
          <p>{roleLabels[persona.role]}</p>
        </div>
      </div>

      <p className="persona-card__description">{persona.description}</p>
      {showEmail ? <p className="persona-card__email">{persona.email}</p> : null}

      <div className="persona-card__permissions" aria-label={`Key permissions for ${persona.name}`}>
        {permissions.visible.map((permission) => (
          <Chip key={permission} className="persona-card__chip">{permission.replace(/:/g, ' ')}</Chip>
        ))}
        {permissions.overflow > 0 ? <Chip className="persona-card__chip persona-card__chip--more">+{permissions.overflow} more</Chip> : null}
      </div>

      {isSelected ? <span className="persona-card__selected-badge">Selected</span> : null}
    </article>
  );
}

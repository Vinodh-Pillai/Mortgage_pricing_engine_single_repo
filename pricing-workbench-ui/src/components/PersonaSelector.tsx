import { Badge } from '../design-system';
import { roleLabels, syntheticPersonas } from '../lib/auth/personas';

export function PersonaSelector({ compact = false }: { compact?: boolean }) {
  if (!import.meta.env.DEV) return null;

  return (
    <section className={compact ? 'persona-selector persona-selector--compact' : 'persona-selector'} aria-label="Development persona reference">
      <div className="persona-selector__current">
        <span className="persona-selector__label">Development personas</span>
        <strong>Reference only</strong>
        <Badge>Real auth required</Badge>
      </div>
      <p className="persona-selector__label">
        Synthetic persona switching is disabled for production auth. Seed real backend users with matching roles instead.
      </p>
      <ul>
        {syntheticPersonas.map((persona) => (
          <li key={persona.id}>{persona.email} — {roleLabels[persona.role]}</li>
        ))}
      </ul>
    </section>
  );
}

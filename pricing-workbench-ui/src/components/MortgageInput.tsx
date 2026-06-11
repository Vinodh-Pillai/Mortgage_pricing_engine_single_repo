import React from 'react';
import type { BorrowerIntake } from '../lib/api/quoteRuns';

export function MortgageInput({
  id,
  label,
  value,
  error,
  placeholder,
  type = 'text',
  onChange,
  as = 'input',
  options = [],
}: {
  id: keyof BorrowerIntake;
  label: string;
  value: string;
  error?: string;
  placeholder?: string;
  type?: 'text' | 'number' | 'email' | 'date';
  onChange: (field: keyof BorrowerIntake, value: string) => void;
  as?: 'input' | 'select';
  options?: Array<{ value: string; label: string }>;
}) {
  const errorId = `${id}-error`;
  if (as === 'select') {
    return (
      <div className="field-group">
        <label htmlFor={id}>{label}</label>
        <select
          id={id}
          name={id}
          value={value}
          aria-invalid={Boolean(error)}
          aria-describedby={error ? errorId : undefined}
          onChange={(event) => onChange(id, event.target.value)}
        >
          {options.map((opt) => (
            <option key={opt.value} value={opt.value}>
              {opt.label}
            </option>
          ))}
        </select>
        {error ? <p id={errorId} role="alert">{error}</p> : null}
      </div>
    );
  }
  return (
    <div className="field-group">
      <label htmlFor={id}>{label}</label>
      <input id={id} name={id} type={type} value={value} placeholder={placeholder} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : undefined} onChange={(event) => onChange(id, event.target.value)} />
      {error ? <p id={errorId} role="alert">{error}</p> : null}
    </div>
  );
}

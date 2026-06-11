import React from 'react';

export function WorkflowField({
  id,
  label,
  value,
  error,
  multiline = false,
  onChange,
}: {
  id: string;
  label: string;
  value: string;
  error?: string;
  multiline?: boolean;
  onChange: (value: string) => void;
}) {
  const errorId = `${id}-error`;
  return (
    <div className={multiline ? 'field-group field-group--full' : 'field-group'}>
      <label htmlFor={id}>{label} <span aria-hidden="true">*</span></label>
      {multiline ? (
        <textarea id={id} value={value} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : undefined} onChange={(event) => onChange(event.target.value)} />
      ) : (
        <input id={id} value={value} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : undefined} onChange={(event) => onChange(event.target.value)} />
      )}
      {error ? <p id={errorId} role="alert">{error}</p> : null}
    </div>
  );
}
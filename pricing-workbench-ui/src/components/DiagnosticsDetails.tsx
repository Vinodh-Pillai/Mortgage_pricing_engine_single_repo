import React from 'react';

export function DiagnosticsDetails({ items }: { items: string[] }) {
  if (!items.length) return null;
  return (
    <details className="trace-badge">
      <summary>Support details</summary>
      <ul>{items.map((item) => <li key={item}>{item}</li>)}</ul>
    </details>
  );
}
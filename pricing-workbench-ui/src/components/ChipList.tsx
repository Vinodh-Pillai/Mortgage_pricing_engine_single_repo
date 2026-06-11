import React from 'react';

export function ChipList({ label, values }: { label: string; values: string[] }) {
  if (!values.length) return <p className="field-help">No {label.toLowerCase()} provided.</p>;
  return <ul className="chip-list" aria-label={label}>{values.map((value) => <li key={value}>{value}</li>)}</ul>;
}
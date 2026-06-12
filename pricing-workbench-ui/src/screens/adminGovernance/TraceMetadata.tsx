import type { AdminTraceMetadata } from '../../lib/api/adminGovernance';

function CopyButton({ label, value }: { label: string; value: string }) {
  return <button type="button" className="button-secondary" aria-label={`Copy ${label}`} data-copy-value={value}>Copy</button>;
}

export function TraceMetadata({ metadata, tenantContext, adminRole }: { metadata: AdminTraceMetadata; tenantContext: string; adminRole: string }) {
  const rows = [
    ['Trace ID', metadata.traceId],
    ['Artifact ID', metadata.artifactId],
    ['Policy version', metadata.policyVersion],
    ['Environment', metadata.environment],
    ['Signer metadata', metadata.signerMetadata],
    ['Tenant context', tenantContext],
    ['Admin role', adminRole],
  ];

  return (
    <dl className="status-grid" aria-label="Admin governance trace metadata">
      {rows.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd><code>{value}</code> <CopyButton label={label} value={value} /></dd>
        </div>
      ))}
    </dl>
  );
}

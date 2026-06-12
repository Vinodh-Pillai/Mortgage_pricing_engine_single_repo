import '@testing-library/jest-dom/vitest';
import { act, cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import ComplianceEvidenceScreen, { exportComplianceEvidenceJson, stateForComplianceEvidence } from './ComplianceEvidenceScreen';
import { complianceEvidenceFixture, emptyComplianceEvidenceFixture } from './fixtures';
import { complianceEvidenceScreenModule } from './index';

afterEach(() => cleanup());

describe('PII-24-S28 compliance evidence registry screen', () => {
  it('defines three route matches and story evidence target', () => {
    expect(complianceEvidenceScreenModule.routePattern).toBe('/compliance/evidence');
    expect(complianceEvidenceScreenModule.evidenceTarget).toBe('.local-harness/evidence/PII-24-S28/compliance-evidence-registry.json');
    expect(complianceEvidenceScreenModule.match('/compliance/evidence')).toBe(true);
    expect(complianceEvidenceScreenModule.match('/privacy/requests')).toBe(true);
    expect(complianceEvidenceScreenModule.match('/security/events')).toBe(true);
    expect(complianceEvidenceScreenModule.stateCoverage).toEqual(expect.arrayContaining(['loading', 'empty', 'blocked', 'ready']));
  });

  it('fetches registry evidence through the compliance API adapter', async () => {
    const fetchImpl = vi.fn(async () => ({ status: 200, json: async () => complianceEvidenceFixture })) as unknown as typeof fetch;
    render(<ComplianceEvidenceScreen fetchImpl={fetchImpl} />);

    await act(async () => { await Promise.resolve(); });

    expect(fetchImpl).toHaveBeenCalledWith('/api/v1/compliance/evidence', expect.objectContaining({ headers: expect.objectContaining({ 'X-Ui-Trace-Id': 'sec-s07-local-trace' }) }));
    expect(await screen.findByRole('heading', { name: 'Compliance Evidence Registry' })).toBeInTheDocument();
  });

  it('renders artifact fields, filters, modal, and redaction export affordance', () => {
    const onEvidenceCapture = vi.fn();
    render(<ComplianceEvidenceScreen evidence={complianceEvidenceFixture} onEvidenceCapture={onEvidenceCapture} />);

    const table = screen.getByRole('table', { name: /Compliance evidence artifacts/i });
    expect(within(table).getByText('artifact-registry-001')).toBeInTheDocument();
    expect(table).toHaveTextContent('DISCLOSURE_PACKAGE');
    expect(within(table).getByText('hash-fixture-artifact-001')).toBeInTheDocument();
    expect(screen.getByLabelText(/Export artifact-registry-001 with redaction profile/i)).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'DISCLOSURE_PACKAGE' } });
    expect(within(table).getByText('artifact-registry-001')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'View Artifact' }));
    expect(screen.getByRole('dialog', { name: /View Artifact artifact-registry-001/i })).toHaveTextContent('Hash verification');
    expect(onEvidenceCapture).toHaveBeenCalledWith(expect.objectContaining({ screenId: 'compliance-evidence-registry', evidenceTarget: '.local-harness/evidence/PII-24-S28/compliance-evidence-registry.json' }));
  });

  it('renders decisions, advisory, fair lending, privacy, security, alerts, retention, and config gaps', () => {
    const onNavigate = vi.fn();
    render(<ComplianceEvidenceScreen evidence={complianceEvidenceFixture} onNavigate={onNavigate} />);

    fireEvent.click(screen.getByRole('button', { name: 'Decisions' }));
    expect(screen.getByRole('table', { name: /Compliance decisions/i })).toHaveTextContent('DISCLOSURE_REVIEW_REQUIRED');
    expect(screen.getByText(/Regulatory citation/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Advisory Reviews' }));
    expect(screen.getByText(/Fair Lending - Pending Review/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Export review review-fixture-001/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Fair Lending' }));
    expect(screen.getByText(/Redaction state: Partial/i)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Export drilldown fair-lending-drilldown-001 with redaction/i })).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Privacy Requests' }));
    expect(screen.getByRole('table', { name: /Privacy requests/i })).toHaveTextContent('BREACHED');
    fireEvent.click(screen.getByRole('button', { name: 'Process Request' }));
    expect(screen.getByRole('dialog', { name: /Process Request privacy-request-001/i })).toHaveTextContent('Identity verification');
    fireEvent.click(screen.getByRole('button', { name: 'Close' }));

    fireEvent.click(screen.getByRole('button', { name: 'Security Events' }));
    expect(screen.getByRole('button', { name: /Acknowledge security-event-001/i })).toBeInTheDocument();
    expect(screen.getByText(/Correlation ID: correlation-001/i)).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: 'Alerts' }));
    fireEvent.click(screen.getByRole('button', { name: 'Route to Owner' }));
    expect(onNavigate).toHaveBeenCalledWith('/admin/governance');

    fireEvent.click(screen.getByRole('button', { name: 'Retention Controls' }));
    expect(screen.getByRole('button', { name: /Trigger deletion for retention-rule-001/i })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Apply Legal Hold' }));
    expect(screen.getByRole('dialog', { name: /Apply Legal Hold retention-rule-001/i })).toHaveTextContent('LEGAL_HOLD_ACTIVE');

    fireEvent.click(screen.getByRole('button', { name: 'Configuration Gaps' }));
    expect(screen.getByText('Fair Lending Jurisdiction Policy Ref Required')).toBeInTheDocument();
  });

  it('exports evidence and represents empty and blocked states', () => {
    render(<ComplianceEvidenceScreen evidence={complianceEvidenceFixture} />);
    fireEvent.click(screen.getByRole('button', { name: 'Export Evidence' }));
    expect((screen.getByLabelText(/Exported compliance evidence/i) as HTMLTextAreaElement).value).toContain('PII-24-S28');
    expect(exportComplianceEvidenceJson(complianceEvidenceFixture)).toContain('compliance-evidence-registry');
    expect(stateForComplianceEvidence(complianceEvidenceFixture)).toBe('blocked');
    expect(stateForComplianceEvidence(emptyComplianceEvidenceFixture)).toBe('empty');
  });
});

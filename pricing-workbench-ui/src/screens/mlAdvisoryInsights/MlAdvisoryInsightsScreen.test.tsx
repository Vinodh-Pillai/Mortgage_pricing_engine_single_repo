import '@testing-library/jest-dom/vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MlAdvisoryInsightsScreen } from './AdvisoryLayout';

describe('MlAdvisoryInsightsScreen', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
        const url = input.toString();
        if (url === '/api/v1/ml-advisory/feedback') {
          return {
            ok: true,
            status: 200,
            json: async () => ({ recommendationId: 'rec-high', status: 'RECORDED', message: 'Feedback recorded', evidenceRef: 'feedback-evidence-ref' }),
          };
        }
        if (url !== '/api/v1/ml-advisory/insights') throw new Error(`Unexpected request ${url}`);
        expect(init?.headers).toEqual(expect.objectContaining({ 'X-Ui-Trace-Id': 'ml-s14-local-trace' }));
        return {
          ok: true,
          status: 200,
          json: async () => ({
            tenantContext: 'ui-preview-tenant',
            dependencyStatus: 'ML_ADVISORY_SERVICE_EVIDENCE_INCOMPLETE',
            uiTraceId: 'ml-s14-local-trace',
            recommendations: [
              {
                recommendationId: 'rec-high',
                modelVersion: 'model-v1',
                confidence: 91,
                explanation: 'Model explanation from API.',
                allowedActions: ['ACCEPT', 'REJECT', 'MANUAL_REVIEW', 'ESCALATE'],
                auditRefs: ['audit-ref-required'],
                automaticDecisionApplied: true,
                featureImportance: ['loan attribute supplied by model'],
                counterfactual: 'Counterfactual supplied by model service.',
              },
            ],
            modelVersions: [
              { modelVersion: 'model-v1', driftStatus: 'STABLE', alertState: 'NO_ACTIVE_ALERT', feedbackLoops: ['feedback-loop-ref-required'], exportEvidenceRefs: ['evidence-export-ref-required'] },
              { modelVersion: 'model-v2', driftStatus: 'CRITICAL', alertState: 'ALERT_REVIEW_REQUIRED', feedbackLoops: [], exportEvidenceRefs: [] },
            ],
            advisoryUnavailable: true,
            fallbackReason: 'MODEL_UNAVAILABLE',
            events: ['MlAdvisoryInsightsOpened'],
          }),
        };
      }),
    );
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('renders recommendations, governance, unavailable recovery, and feedback submission from API-supplied values', async () => {
    render(<MlAdvisoryInsightsScreen />);

    expect(await screen.findByRole('heading', { name: 'ML Advisory Insights' })).toBeInTheDocument();
    expect(screen.getByLabelText('Model version')).toHaveValue('model-v1');
    expect(screen.getAllByText('Advisory unavailable')).toHaveLength(2);
    expect(screen.getByText('Use manual pricing workflow')).toHaveAttribute('href', '/quote/start');

    const recommendations = screen.getByRole('table', { name: 'ML advisory recommendations' });
    expect(within(recommendations).getByText('rec-high')).toBeInTheDocument();
    expect(within(recommendations).getByText('HIGH')).toBeInTheDocument();
    expect(within(recommendations).getByText('ACCEPT')).toBeInTheDocument();
    expect(within(recommendations).getByText('Automatic decision applied')).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Model version governance' })).toHaveTextContent('CRITICAL');
    expect(screen.getByRole('button', { name: 'Trigger Retrain' })).toBeDisabled();

    fireEvent.click(screen.getByRole('button', { name: 'Provide Feedback' }));
    const dialog = screen.getByRole('dialog', { name: 'Provide Feedback' });
    fireEvent.change(within(dialog).getByLabelText('Comment'), { target: { value: 'Helpful context.' } });
    fireEvent.change(within(dialog).getByLabelText('Outcome'), { target: { value: 'MODIFIED' } });
    fireEvent.change(within(dialog).getByLabelText('Modified values'), { target: { value: 'Analyst-entered modification note.' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Submit feedback' }));

    expect(await screen.findByText('Feedback recorded')).toBeInTheDocument();
    await waitFor(() => expect(fetch).toHaveBeenCalledWith('/api/v1/ml-advisory/feedback', expect.objectContaining({ method: 'POST' })));
    expect(document.body.textContent).not.toMatch(/rate table|eligibility threshold|fee amount/i);
  });
});

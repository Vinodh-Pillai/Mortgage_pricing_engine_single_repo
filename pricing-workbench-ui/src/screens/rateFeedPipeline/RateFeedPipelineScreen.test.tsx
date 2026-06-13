import '@testing-library/jest-dom/vitest';
import { render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { RateFeedPipelineScreen } from './RateFeedPipelineScreen';

describe('RateFeedPipelineScreen', () => {
  it('renders pipeline table and detail fallback data', async () => {
    render(<RateFeedPipelineScreen tenantId="ui-preview-tenant" />);

    await waitFor(() => expect(screen.getByRole('heading', { name: 'Rate Feed → Rule Book Pipeline' })).toBeInTheDocument());
    expect(screen.getByText('FNMA_LLPA_2026_06')).toBeInTheDocument();
    expect(screen.getByText('Pipeline Detail: FNMA_LLPA_2026_06')).toBeInTheDocument();
    expect(screen.getByText(/Expected: \+75 bps/)).toBeInTheDocument();
  });
});

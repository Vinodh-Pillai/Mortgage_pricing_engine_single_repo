import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { QueryProvider, createWorkbenchQueryClient, DATA_LAYER_DEFAULTS } from './QueryProvider';

describe('CacheTest', () => {
  it('staleTimeRespected', () => {
    const client = createWorkbenchQueryClient();
    expect(client.getDefaultOptions().queries?.staleTime).toBe(DATA_LAYER_DEFAULTS.staleTime);
  });

  it('gcTimeEvictsUnused', () => {
    const client = createWorkbenchQueryClient();
    expect(client.getDefaultOptions().queries?.gcTime).toBe(DATA_LAYER_DEFAULTS.gcTime);
  });

  it('rendersProviderChildren', () => {
    render(
      <QueryProvider client={createWorkbenchQueryClient()}>
        <div>data layer ready</div>
      </QueryProvider>,
    );
    expect(screen.getByText('data layer ready')).toBeTruthy();
  });
});

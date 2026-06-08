export type UiHealth = {
  service: string;
  status: string;
  ready: boolean;
  dependencyStatus: string;
  correlationId?: string | null;
  dependencies: string[];
};

export async function fetchUiHealth(fetchImpl: typeof fetch = fetch): Promise<UiHealth> {
  const response = await fetchImpl('/api/ui/health', {
    headers: { Accept: 'application/json' },
  });

  if (!response.ok) {
    throw new Error(`BFF status request failed with ${response.status}`);
  }

  return response.json() as Promise<UiHealth>;
}

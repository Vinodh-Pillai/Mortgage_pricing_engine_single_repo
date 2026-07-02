export function displayChannelLabel(value: string | null | undefined): string {
  if (!value) return 'Not provided';
  const normalized = value.trim().toUpperCase().replace(/[\s-]+/g, '_');
  const mapped: Record<string, string> = {
    RETAIL: 'Retail',
    WHOLESALE: 'Wholesale',
    CORR: 'Correspondent',
    CORRESPONDENT: 'Correspondent',
    TPO: 'TPO',
    CONSUMER_DIRECT: 'Consumer Direct',
  };
  return mapped[normalized] ?? value.toLowerCase().split(/[_\s-]+/).filter(Boolean).map((part) => part[0].toUpperCase() + part.slice(1)).join(' ');
}

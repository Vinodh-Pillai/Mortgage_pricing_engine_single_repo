import { useState } from 'react';
import { ChipList } from '../mlAdvisoryInsights/shared';
import type { FeatureDriftRow } from './types';

export function FeatureDrift({ rows }: { rows: FeatureDriftRow[] }) {
  const [selectedFeature, setSelectedFeature] = useState<FeatureDriftRow | null>(null);
  return (
    <div role="tabpanel" aria-label="Feature Drift">
      <div className="panel-heading-row">
        <div>
          <h3>Feature drift</h3>
          <p className="field-help">PSI thresholds: STABLE &lt; 0.1, WARNING 0.1-0.2, CRITICAL &gt; 0.2. KS p-value &lt; 0.05 indicates significant drift.</p>
        </div>
        <button type="button" onClick={() => window.dispatchEvent(new CustomEvent('drift-export', { detail: 'feature' }))}>Export Feature Drift</button>
      </div>
      <div className="quote-table" role="table" aria-label="Feature drift metrics">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Feature Name</span><span role="columnheader">PSI</span><span role="columnheader">KS Statistic</span><span role="columnheader">p-value</span><span role="columnheader">Histogram</span><span role="columnheader">Drift Status</span><span role="columnheader">Detail</span>
        </div>
        {rows.map((row) => (
          <div key={row.featureName} role="row" className="quote-table__row">
            <span role="cell"><strong>{row.featureName}</strong></span><span role="cell">{row.psi.toFixed(2)}</span><span role="cell">{row.ksStatistic.toFixed(2)}</span><span role="cell">{row.pValue.toFixed(2)}</span><span role="cell"><ChipList label={`${row.featureName} histogram comparison`} values={row.histogram} /></span><span role="cell">{row.status}</span><span role="cell"><button type="button" onClick={() => setSelectedFeature(row)}>Open detail</button></span>
          </div>
        ))}
      </div>
      {selectedFeature ? (
        <div role="dialog" aria-modal="true" aria-labelledby="feature-detail-heading" className="panel">
          <h3 id="feature-detail-heading">{selectedFeature.featureName} drift detail</h3>
          <p>Reference vs current histogram and PSI over time are shown from connected drift evidence refs.</p>
          <ChipList label="Reference versus current histogram" values={selectedFeature.histogram} />
          <dl><dt>PSI evidence</dt><dd><code>{selectedFeature.evidenceRef}</code></dd><dt>KS test detail</dt><dd>KS p-value {selectedFeature.pValue.toFixed(2)}</dd></dl>
          <button type="button" onClick={() => setSelectedFeature(null)}>Close</button>
        </div>
      ) : null}
    </div>
  );
}

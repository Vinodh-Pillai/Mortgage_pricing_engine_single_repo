import { ChipList } from '../mlAdvisoryInsights/shared';
import type { PopulationStabilityView } from './types';

export function PopulationStability({ population }: { population: PopulationStabilityView }) {
  return (
    <div role="tabpanel" aria-label="Population Stability">
      <div className="panel-heading-row">
        <div>
          <h3>Population stability</h3>
          <p className="field-help">PSI over time includes story thresholds at 0.1 and 0.2, plus cohort drilldown by channel, product, region, and time.</p>
        </div>
        <button type="button" onClick={() => window.dispatchEvent(new CustomEvent('drift-export', { detail: 'population' }))}>Export Population Stability</button>
      </div>
      <section className="panel" aria-label="PSI over time chart">
        <h4>PSI Over Time</h4>
        <ChipList label="PSI threshold lines" values={['threshold 0.1', 'threshold 0.2']} />
        <ol>{population.psiOverTime.map((point) => <li key={point.bucket}>{point.bucket}: {point.psi.toFixed(2)}</li>)}</ol>
      </section>
      <div className="quote-table" role="table" aria-label="Cohort drift drilldown">
        <div role="row" className="quote-table__row quote-table__row--head"><span role="columnheader">Cohort</span><span role="columnheader">PSI</span><span role="columnheader">Top Feature</span><span role="columnheader">Status</span></div>
        {population.cohorts.map((cohort) => <div key={cohort.cohort} role="row" className="quote-table__row"><span role="cell">{cohort.cohort}</span><span role="cell">{cohort.psi.toFixed(2)}</span><span role="cell">{cohort.topFeature}</span><span role="cell">{cohort.status}</span></div>)}
      </div>
    </div>
  );
}

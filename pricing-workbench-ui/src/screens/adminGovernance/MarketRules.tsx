import type { MarketRuleSummary } from '../../lib/api/adminGovernance';
import { ChipList } from './shared';

export function MarketRules({ rules }: { rules: MarketRuleSummary[] }) {
  return (
    <section className="panel" aria-labelledby="market-rules-heading">
      <h2 id="market-rules-heading">Market Rules</h2>
      <div className="quote-table" role="table" aria-label="Market rules">
        <div role="row" className="quote-table__row quote-table__row--head">
          <span role="columnheader">Rule ID</span>
          <span role="columnheader">Type</span>
          <span role="columnheader">Staging status</span>
          <span role="columnheader">Promotion disabled</span>
          <span role="columnheader">Completeness gate</span>
        </div>
        {rules.map((rule) => (
          <div key={rule.ruleId} role="row" className="quote-table__row">
            <span role="cell">{rule.ruleId}</span>
            <span role="cell">{rule.ruleType}</span>
            <span role="cell">{rule.stagingStatus}</span>
            <span role="cell">{rule.promotionDisabled ? 'yes' : 'no'}<details><summary>View Missing Fields</summary><ChipList label={`${rule.ruleId} missing required fields`} values={rule.missingRequiredFields} /></details></span>
            <span role="cell"><strong>Market guidance promotion blocked</strong><br />{rule.completenessGate}</span>
          </div>
        ))}
      </div>
    </section>
  );
}

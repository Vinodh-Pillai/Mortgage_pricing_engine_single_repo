import { useState } from 'react';
import type { AdvisoryRecommendationInsight } from '../../lib/api/mlAdvisoryInsights';
import { ChipList, businessFacingText } from './shared';
import { FeedbackModal } from './FeedbackModal';

function confidenceLabel(confidence: string | number) {
  if (typeof confidence === 'number') {
    if (confidence > 80) return 'HIGH';
    if (confidence >= 50) return 'MEDIUM';
    return 'LOW';
  }
  const parsed = Number(String(confidence).replace('%', ''));
  if (Number.isFinite(parsed)) return confidenceLabel(parsed);
  return 'API supplied confidence';
}

export function Recommendations({ recommendations }: { recommendations: AdvisoryRecommendationInsight[] }) {
  const [feedbackRecommendation, setFeedbackRecommendation] = useState<AdvisoryRecommendationInsight | null>(null);

  return (
    <section className="panel" aria-labelledby="ml-advisory-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Recommendation evidence</p>
          <h2 id="ml-advisory-heading">Advisory recommendation review</h2>
        </div>
      </div>
      {recommendations.length === 0 ? (
        <div className="banner banner--info" role="status">No recommendations at this time</div>
      ) : (
        <div className="quote-table" role="table" aria-label="ML advisory recommendations">
          <div role="row" className="quote-table__row quote-table__row--head">
            <span role="columnheader">Recommendation</span>
            <span role="columnheader">Model version</span>
            <span role="columnheader">Confidence</span>
            <span role="columnheader">Explanation and actions</span>
            <span role="columnheader">Review references</span>
          </div>
          {recommendations.map((recommendation) => (
            <div role="row" className="quote-table__row" key={recommendation.recommendationId}>
              <span role="cell"><strong>{recommendation.recommendationId}</strong></span>
              <span role="cell">{recommendation.modelVersion}</span>
              <span role="cell"><strong>{confidenceLabel(recommendation.confidence)}</strong><br />{recommendation.confidence}</span>
              <span role="cell">
                <details>
                  <summary>{recommendation.explanation}</summary>
                  <ChipList label={`${recommendation.recommendationId} feature importance`} values={recommendation.featureImportance ?? []} />
                  {recommendation.counterfactual ? <p>{businessFacingText(recommendation.counterfactual)}</p> : <p className="field-help">Counterfactual evidence was not supplied by the API.</p>}
                </details>
                <ChipList label={`${recommendation.recommendationId} allowed actions`} values={recommendation.allowedActions} />
                {recommendation.automaticDecisionApplied ? <strong>Automatic decision applied</strong> : <span className="field-help">No automatic pricing decision applied.</span>}
                <button type="button" onClick={() => setFeedbackRecommendation(recommendation)}>Provide Feedback</button>
              </span>
              <span role="cell">
                <ChipList label={`${recommendation.recommendationId} review references`} values={recommendation.auditRefs} />
                <a href={`/audit/replay?ref=${encodeURIComponent(recommendation.auditRefs[0] ?? recommendation.recommendationId)}`}>View Audit</a>
              </span>
            </div>
          ))}
        </div>
      )}
      {feedbackRecommendation ? <FeedbackModal recommendation={feedbackRecommendation} onClose={() => setFeedbackRecommendation(null)} /> : null}
    </section>
  );
}

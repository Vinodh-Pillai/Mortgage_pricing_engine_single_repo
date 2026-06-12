import { type FormEvent, useState } from 'react';
import { submitMlAdvisoryFeedback, type AdvisoryRecommendationInsight, type MlAdvisoryFeedbackOutcome, type MlAdvisoryFeedbackResult } from '../../lib/api/mlAdvisoryInsights';

const outcomes: MlAdvisoryFeedbackOutcome[] = ['ACCEPTED', 'REJECTED', 'MODIFIED'];

export function FeedbackModal({ recommendation, onClose }: { recommendation: AdvisoryRecommendationInsight; onClose: () => void }) {
  const [rating, setRating] = useState('5');
  const [comment, setComment] = useState('');
  const [outcome, setOutcome] = useState<MlAdvisoryFeedbackOutcome>('ACCEPTED');
  const [modifiedValues, setModifiedValues] = useState('');
  const [submitState, setSubmitState] = useState<{ kind: 'idle' } | { kind: 'submitting' } | { kind: 'submitted'; result: MlAdvisoryFeedbackResult } | { kind: 'failed'; message: string }>({ kind: 'idle' });

  async function submitFeedback(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitState({ kind: 'submitting' });
    try {
      const result = await submitMlAdvisoryFeedback({
        recommendationId: recommendation.recommendationId,
        modelVersion: recommendation.modelVersion,
        rating: Number(rating),
        comment,
        outcome,
        modifiedValues: outcome === 'MODIFIED' ? modifiedValues : undefined,
      });
      setSubmitState({ kind: 'submitted', result });
    } catch (error: unknown) {
      setSubmitState({ kind: 'failed', message: error instanceof Error ? error.message : 'Feedback could not be submitted.' });
    }
  }

  return (
    <div role="dialog" aria-modal="true" aria-labelledby="ml-feedback-heading" className="modal-card">
      <h2 id="ml-feedback-heading">Provide Feedback</h2>
      <p className="field-help">Recommendation {recommendation.recommendationId} from model {recommendation.modelVersion}</p>
      <form className="intake-form" onSubmit={submitFeedback}>
        <label htmlFor="ml-feedback-rating">Rating</label>
        <select id="ml-feedback-rating" value={rating} onChange={(event) => setRating(event.target.value)}>
          {[1, 2, 3, 4, 5].map((value) => <option key={value} value={value}>{value} star{value === 1 ? '' : 's'}</option>)}
        </select>

        <label htmlFor="ml-feedback-comment">Comment</label>
        <textarea id="ml-feedback-comment" value={comment} onChange={(event) => setComment(event.target.value)} />

        <label htmlFor="ml-feedback-outcome">Outcome</label>
        <select id="ml-feedback-outcome" value={outcome} onChange={(event) => setOutcome(event.target.value as MlAdvisoryFeedbackOutcome)}>
          {outcomes.map((value) => <option key={value} value={value}>{value}</option>)}
        </select>

        {outcome === 'MODIFIED' ? (
          <>
            <label htmlFor="ml-feedback-modified-values">Modified values</label>
            <textarea id="ml-feedback-modified-values" value={modifiedValues} onChange={(event) => setModifiedValues(event.target.value)} />
          </>
        ) : null}

        <button type="submit" disabled={submitState.kind === 'submitting'}>Submit feedback</button>
        <button type="button" className="button-secondary" onClick={onClose}>Close</button>
      </form>
      {submitState.kind === 'submitted' ? <div className="banner banner--info" role="status">{submitState.result.message || submitState.result.status}</div> : null}
      {submitState.kind === 'failed' ? <div className="banner banner--blocked" role="alert">{submitState.message}</div> : null}
    </div>
  );
}

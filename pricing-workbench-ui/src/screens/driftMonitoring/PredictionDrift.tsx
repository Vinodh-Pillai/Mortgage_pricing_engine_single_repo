import type { PredictionDriftView } from './types';

export function PredictionDrift({ prediction }: { prediction: PredictionDriftView }) {
  return (
    <div role="tabpanel" aria-label="Prediction Drift">
      <div className="panel-heading-row">
        <div>
          <h3>Prediction drift</h3>
          <p className="field-help">Mean shift &gt; 2 std dev is WARNING; &gt; 3 std dev is CRITICAL. Accuracy drift renders only when labeled data exists.</p>
        </div>
        <button type="button" onClick={() => window.dispatchEvent(new CustomEvent('drift-export', { detail: 'prediction' }))}>Export Prediction Drift</button>
      </div>
      <div className="module-rail__grid" role="list" aria-label="Prediction drift cards">
        <article className="module-card" role="listitem"><strong>Mean Shift</strong><p>Current vs reference mean shift: {prediction.meanShiftStdDev.toFixed(1)} std dev</p><p>Status: {prediction.meanShiftStatus}</p></article>
        <article className="module-card" role="listitem"><strong>Distribution Shift</strong><p>{prediction.distributionShift}</p><p>Histogram overlay is supplied by the drift API evidence payload.</p></article>
        <article className="module-card" role="listitem"><strong>Accuracy Drift</strong><p>{prediction.accuracy === null ? prediction.accuracyState : `${prediction.accuracy}% accuracy`}</p><p>Calibration plot: <code>{prediction.calibrationRef}</code></p></article>
      </div>
    </div>
  );
}

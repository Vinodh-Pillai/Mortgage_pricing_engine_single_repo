import type { DraftBackup, DraftScenario } from './draft';

export function ResumeDraft({
  draftId,
  backup,
  loading,
  error,
  onResume,
  onDismiss,
}: {
  draftId?: string | null;
  backup?: DraftBackup | null;
  loading?: boolean;
  error?: string;
  onResume: () => void;
  onDismiss: () => void;
}) {
  if (!draftId && !backup && !loading && !error) return null;
  return (
    <div className="quote-intake-resume" role={error ? 'alert' : 'status'}>
      <div>
        <strong>{error ? 'Draft resume needs attention' : loading ? 'Loading saved draft' : 'Saved draft available'}</strong>
        <p>{resumeCopy(draftId, backup, loading, error)}</p>
      </div>
      <div className="quote-intake-resume__actions">
        {!loading && !error ? <button type="button" onClick={onResume}>Resume draft</button> : null}
        <button type="button" onClick={onDismiss}>Start new</button>
      </div>
    </div>
  );
}

export function draftToBackup(draft: DraftScenario, currentStep = 1): DraftBackup {
  return {
    scenarioId: draft.scenarioId,
    scenarioVersion: draft.scenarioVersion,
    currentStep,
    intake: draft.intake ?? {},
    savedAt: new Date().toISOString(),
    status: draft.status,
  };
}

function resumeCopy(draftId?: string | null, backup?: DraftBackup | null, loading?: boolean, error?: string) {
  if (error) return error;
  if (loading) return `Loading draft ${draftId ?? ''} from scenario-service.`.trim();
  if (backup) return `Draft ${backup.scenarioId} saved at ${new Date(backup.savedAt).toLocaleString()} on step ${backup.currentStep}.`;
  return `Draft ${draftId} can be loaded from scenario-service.`;
}

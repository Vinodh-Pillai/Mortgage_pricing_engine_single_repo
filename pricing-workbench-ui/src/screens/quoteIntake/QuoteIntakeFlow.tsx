import { type FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import type { BorrowerIntake, LaunchState, MetadataState, QuoteRunLaunch, ScenarioIntakeField } from '../../lib/api/quoteRuns';
import { createDraftScenario, getDraftScenario, loadDraftBackup, saveDraftBackup, updateDraftScenario, validateDraftSection, type DraftBackup } from './draft';
import { launchQuoteRun } from './launch';
import { fieldsForStep, normalizeMetadataState, quoteIntakeSteps, type QuoteIntakeStepId } from './metadata';
import { errorsToValidation, firstInvalidField, validateFields, type IntakeFieldErrors } from './validation';
import { ProgressIndicator, type StepStatus } from './ProgressIndicator';
import { ResumeDraft, draftToBackup } from './ResumeDraft';
import { Step1ScenarioIdentity, Step2BorrowerCredit, Step3LoanStructure, Step4Property, Step5IncomeAssets, Step6Preferences } from './steps';
import './QuoteIntake.css';

export type QuoteIntakeFlowProps = {
  tenantId?: string;
  intake?: BorrowerIntake;
  errors?: IntakeFieldErrors;
  launchState?: LaunchState;
  metadataState: MetadataState;
  onChange?: (field: keyof BorrowerIntake, value: string) => void;
  onRetry?: () => void;
  onSubmit?: (event: FormEvent<HTMLFormElement>) => void;
  onNavigate?: (route: string) => void;
  onEvidenceCapture?: (event: Record<string, unknown>) => void;
};

const tenantBoundaryPlaceholder = 'ui-preview-tenant';

export const initialQuoteIntake: BorrowerIntake = {
  quoteIntent: '',
  channel: '',
  scenarioName: '',
  externalLoanId: '',
  sourceSystem: 'PRICING_WORKBENCH',
  borrowerName: '',
  borrowerRole: 'PRIMARY',
  coBorrowerName: '',
  coBorrowerRole: 'CO_BORROWER',
  contactEmail: '',
  creditStatus: 'AVAILABLE',
  creditScore: '',
  creditScoreSource: 'TRI_MERGE',
  creditReportDate: '',
  creditReadiness: '',
  loanPurpose: '',
  loanAmount: '',
  purchasePriceOrValue: '',
  downPaymentOrEquity: '',
  subordinateFinancingAmount: '0',
  helocDrawnAmount: '0',
  helocLimitAmount: '0',
  lienPosition: 'FIRST',
  termMonths: '360',
  amortizationType: 'FIXED',
  requestedLockPeriodDays: '30',
  propertyState: '',
  propertyCounty: '',
  propertyZip: '',
  propertyType: 'SINGLE_FAMILY',
  occupancyType: 'PRIMARY_RESIDENCE',
  unitCount: '1',
  purchasePrice: '',
  appraisedValue: '',
  condoProjectType: '',
  manufacturedHomeFlag: 'false',
  monthlyIncome: '',
  incomeType: 'W2',
  employmentType: 'SALARIED',
  monthlyDebt: '',
  suppliedDti: '',
  reserveMonths: '',
  incomeVerificationStatus: 'VERIFIED',
  assetVerificationStatus: 'VERIFIED',
  liquidAssets: '',
  reserves: '',
  productFamily: '',
  productPreference: '',
  quoteFilters: '',
  effectiveDate: '',
  actorId: '',
  clientContext: '',
};

export function QuoteIntakeFlow({
  tenantId = tenantBoundaryPlaceholder,
  intake,
  errors = {},
  launchState,
  metadataState,
  onChange,
  onRetry,
  onNavigate,
  onEvidenceCapture,
}: QuoteIntakeFlowProps) {
  const [localIntake, setLocalIntake] = useState<BorrowerIntake>(intake ?? initialQuoteIntake);
  const [activeStep, setActiveStep] = useState<QuoteIntakeStepId>(1);
  const [scenarioId, setScenarioId] = useState<string | null>(null);
  const [scenarioVersion, setScenarioVersion] = useState(0);
  const [localErrors, setLocalErrors] = useState<IntakeFieldErrors>({});
  const [completedSteps, setCompletedSteps] = useState<Set<QuoteIntakeStepId>>(new Set());
  const [flowState, setFlowState] = useState<LaunchState>(launchState ?? { kind: 'idle' });
  const [resumeBackup, setResumeBackup] = useState<DraftBackup | null>(() => loadDraftBackup(draftIdFromLocation() ?? undefined));
  const [resumeDismissed, setResumeDismissed] = useState(false);
  const [resumeLoading, setResumeLoading] = useState(false);
  const [resumeError, setResumeError] = useState('');
  const [statusMessage, setStatusMessage] = useState('');
  const cardRef = useRef<HTMLFieldSetElement | null>(null);
  const values = intake ?? localIntake;
  const mergedErrors = { ...errors, ...localErrors };
  const { metadata } = normalizeMetadataState(metadataState);
  const currentStep = quoteIntakeSteps.find((step) => step.id === activeStep) ?? quoteIntakeSteps[0];
  const currentFields = useMemo(() => fieldsForStep(metadata, activeStep), [metadata, activeStep]);
  const draftId = draftIdFromLocation();

  useEffect(() => {
    if (intake) setLocalIntake(intake);
  }, [intake]);

  useEffect(() => {
    if (!draftId || resumeBackup || resumeDismissed) return;
    let cancelled = false;
    setResumeLoading(true);
    getDraftScenario(tenantId, draftId)
      .then((draft) => {
        if (!cancelled) setResumeBackup(draftToBackup(draft));
      })
      .catch((error: unknown) => {
        if (!cancelled) setResumeError(error instanceof Error ? error.message : 'Draft scenario could not be loaded.');
      })
      .finally(() => {
        if (!cancelled) setResumeLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [draftId, resumeBackup, resumeDismissed, tenantId]);

  useEffect(() => {
    cardRef.current?.focus();
  }, [activeStep]);

  function changeField(field: keyof BorrowerIntake, value: string) {
    setLocalIntake((current) => ({ ...current, [field]: value }));
    setLocalErrors((current) => ({ ...current, [field]: undefined }));
    onChange?.(field, value);
  }

  async function continueStep() {
    const clientErrors = validateFields(currentFields, values);
    if (Object.keys(clientErrors).length > 0) {
      setLocalErrors(clientErrors);
      setFlowState({ kind: 'blocked', validation: errorsToValidation(clientErrors) });
      focusFirstInvalid(clientErrors);
      capture('validation-error', { step: activeStep, fields: Object.keys(clientErrors) });
      return;
    }

    setFlowState({ kind: 'submitting' });
    try {
      if (activeStep === 1 && !scenarioId) {
        const draft = await createDraftScenario(tenantId, pickFields(values, currentFields));
        setScenarioId(draft.scenarioId);
        setScenarioVersion(draft.scenarioVersion);
        saveDraftBackup(draft.scenarioId, draft.scenarioVersion, activeStep, values);
        capture('draft-created', { scenarioId: draft.scenarioId, scenarioVersion: draft.scenarioVersion });
      } else if (activeStep < 6) {
        const nextDraft = await updateDraftScenario(tenantId, requireScenarioId(), scenarioVersion, currentStep.section, pickFields(values, currentFields));
        setScenarioVersion(nextDraft.scenarioVersion);
        saveDraftBackup(nextDraft.scenarioId, nextDraft.scenarioVersion, activeStep, values);
        await applyServerDraftValidation(nextDraft.scenarioId, nextDraft.scenarioVersion);
        capture('draft-updated', { scenarioId: nextDraft.scenarioId, scenarioVersion: nextDraft.scenarioVersion, section: currentStep.section });
      } else {
        await launchCurrentQuote();
        return;
      }

      markComplete(activeStep);
      setFlowState({ kind: 'idle' });
      setStatusMessage(`Step ${activeStep} saved.`);
      if (activeStep < 6) setActiveStep((activeStep + 1) as QuoteIntakeStepId);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Quote intake step could not be saved.';
      setFlowState({ kind: 'outage', message });
      setStatusMessage(message);
      capture('step-blocked', { step: activeStep, message });
    }
  }

  async function launchCurrentQuote() {
    const finalDraft = await updateDraftScenario(tenantId, requireScenarioId(), scenarioVersion || 1, currentStep.section, pickFields(values, currentFields));
    setScenarioVersion(finalDraft.scenarioVersion);
    saveDraftBackup(finalDraft.scenarioId, finalDraft.scenarioVersion, activeStep, values);
    capture('draft-updated', { scenarioId: finalDraft.scenarioId, scenarioVersion: finalDraft.scenarioVersion, section: currentStep.section });
    const launch = await launchQuoteRun(tenantId, finalDraft.scenarioId, finalDraft.scenarioVersion, values);
    if (launch.kind === 'blocked') {
      setLocalErrors(launch.validation.blockers);
      setFlowState({ kind: 'blocked', validation: launch.validation });
      focusFirstInvalid(launch.validation.blockers);
      capture('quote-launch-blocked', { blockers: launch.blockers });
      return;
    }
    if (launch.kind === 'needs-attention') {
      setFlowState({ kind: 'blocked', validation: { passed: false, status: 'BLOCKED', message: launch.message, blockers: {} } });
      setStatusMessage(launch.message);
      capture('quote-launch-needs-attention', { blockers: launch.blockers });
      return;
    }
    markComplete(6);
    setFlowState({ kind: 'created', launch: launch.launch });
    capture('quote-launch-created', { runId: launch.launch.runId, nextRoute: launch.launch.nextRoute });
    if (launch.launch.nextRoute) onNavigate?.(launch.launch.nextRoute);
  }

  async function applyServerDraftValidation(nextScenarioId: string, nextVersion: number) {
    const validation = await validateDraftSection(tenantId, nextScenarioId, nextVersion, currentStep.section);
    if (!validation.passed) {
      setLocalErrors(validation.blockers);
      setFlowState({ kind: 'blocked', validation });
      throw new Error(validation.message);
    }
  }

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void continueStep();
  }

  function saveDraft() {
    const resolvedScenarioId = scenarioId ?? resumeBackup?.scenarioId ?? 'local-unsynced-draft';
    saveDraftBackup(resolvedScenarioId, scenarioVersion || resumeBackup?.scenarioVersion || 0, activeStep, values);
    setStatusMessage('Draft saved for resume.');
    capture('draft-saved', { scenarioId: resolvedScenarioId, step: activeStep });
  }

  function previousStep() {
    setActiveStep((step) => Math.max(1, step - 1) as QuoteIntakeStepId);
  }

  function selectStep(step: QuoteIntakeStepId) {
    if (step <= activeStep || completedSteps.has(step)) setActiveStep(step);
  }

  function resumeDraft() {
    if (!resumeBackup) return;
    setScenarioId(resumeBackup.scenarioId);
    setScenarioVersion(resumeBackup.scenarioVersion);
    setLocalIntake((current) => ({ ...current, ...resumeBackup.intake }));
    Object.entries(resumeBackup.intake).forEach(([field, value]) => {
      if (typeof value === 'string') onChange?.(field as keyof BorrowerIntake, value);
    });
    setActiveStep(Math.min(6, Math.max(1, resumeBackup.currentStep)) as QuoteIntakeStepId);
    setResumeDismissed(true);
    setStatusMessage(`Draft ${resumeBackup.scenarioId} resumed.`);
  }

  function onKeyDown(event: React.KeyboardEvent<HTMLFormElement>) {
    if (event.key === 'Escape') {
      event.preventDefault();
      previousStep();
      return;
    }
    const target = event.target as HTMLElement;
    if (event.key === 'Enter' && target.tagName !== 'TEXTAREA' && target.tagName !== 'BUTTON') {
      event.preventDefault();
      void continueStep();
    }
  }

  function requireScenarioId() {
    const resolved = scenarioId ?? resumeBackup?.scenarioId;
    if (!resolved) throw new Error('Create or resume a draft scenario before continuing.');
    return resolved;
  }

  function markComplete(step: QuoteIntakeStepId) {
    setCompletedSteps((current) => new Set([...current, step]));
  }

  function focusFirstInvalid(nextErrors: IntakeFieldErrors) {
    const invalid = firstInvalidField(nextErrors);
    if (invalid) document.getElementById(invalid)?.focus();
  }

  function capture(action: string, detail: Record<string, unknown>) {
    onEvidenceCapture?.({ action, storyId: 'PII-25-S05', ...detail });
  }

  const statuses = quoteIntakeSteps.reduce((acc, step) => {
    acc[step.id] = mergedStepStatus(step.id, completedSteps, mergedErrors, values, fieldsForStep(metadata, step.id));
    return acc;
  }, {} as Record<QuoteIntakeStepId, StepStatus>);

  return (
    <section id="borrower-intake" className="quote-intake-shell" aria-labelledby="intake-heading">
      <div className="quote-intake-hero">
        <p className="eyebrow">Pipeline</p>
        <h2 id="intake-heading">New prospect intake</h2>
        <p>Capture the borrower and loan facts needed to start a pricing run.</p>
      </div>

      <ResumeDraft draftId={draftId} backup={resumeDismissed ? null : resumeBackup} loading={resumeLoading} error={resumeError} onResume={resumeDraft} onDismiss={() => setResumeDismissed(true)} />
      <LaunchBanner state={flowState} onRetry={() => { setFlowState({ kind: 'idle' }); onRetry?.(); }} />
      {statusMessage ? <p className="quote-intake-status" role="status">{statusMessage}</p> : null}

      <div className="quote-intake-layout">
        <ProgressIndicator steps={quoteIntakeSteps} activeStep={activeStep} statuses={statuses} onStepSelect={selectStep} />
        <form className="quote-intake-form" onSubmit={submit} onKeyDown={onKeyDown} noValidate>
          <fieldset ref={cardRef} className="quote-intake-card" tabIndex={-1} aria-labelledby={`quote-step-${activeStep}-heading`}>
            <legend>
              <span>Step {activeStep} of 6</span>
              <strong id={`quote-step-${activeStep}-heading`}>{currentStep.label}</strong>
            </legend>
            <p className="quote-intake-help">{currentStep.summary}</p>
            {renderCurrentStep(activeStep, currentFields, values, mergedErrors, changeField)}
            <div className="quote-intake-actions" aria-label="Step actions">
              <button type="button" onClick={previousStep} disabled={activeStep === 1}>Previous</button>
              <button type="button" onClick={saveDraft}>Save draft</button>
              <button type="submit" disabled={flowState.kind === 'submitting'}>{buttonLabel(activeStep, scenarioId, flowState)}</button>
            </div>
          </fieldset>
        </form>
      </div>
    </section>
  );
}

function renderCurrentStep(step: QuoteIntakeStepId, fields: ScenarioIntakeField[], intake: BorrowerIntake, errors: IntakeFieldErrors, onChange: (field: keyof BorrowerIntake, value: string) => void) {
  const props = { fields, intake, errors, onChange };
  if (step === 1) return <Step1ScenarioIdentity {...props} />;
  if (step === 2) return <Step2BorrowerCredit {...props} />;
  if (step === 3) return <Step3LoanStructure {...props} />;
  if (step === 4) return <Step4Property {...props} />;
  if (step === 5) return <Step5IncomeAssets {...props} />;
  return <Step6Preferences {...props} />;
}

function buttonLabel(step: QuoteIntakeStepId, scenarioId: string | null, state: LaunchState) {
  if (state.kind === 'submitting') return step === 6 ? 'Launching quote...' : 'Saving step...';
  if (step === 1 && !scenarioId) return 'Create draft and continue';
  if (step === 6) return 'Launch quote run';
  return 'Save and continue';
}

function mergedStepStatus(step: QuoteIntakeStepId, completed: Set<QuoteIntakeStepId>, errors: IntakeFieldErrors, intake: BorrowerIntake, fields: ScenarioIntakeField[]): StepStatus {
  if (fields.some((field) => errors[field.fieldId])) return 'error';
  if (completed.has(step)) return 'complete';
  if (fields.some((field) => intake[field.fieldId]?.trim())) return 'in-progress';
  return 'empty';
}

function pickFields(values: BorrowerIntake, fields: ScenarioIntakeField[]) {
  return fields.reduce((acc, field) => {
    acc[field.fieldId] = values[field.fieldId];
    return acc;
  }, {} as Partial<BorrowerIntake>);
}

function draftIdFromLocation() {
  if (typeof window === 'undefined') return null;
  const params = new URLSearchParams(window.location.search);
  return params.get('draft') ?? params.get('scenarioId');
}

function LaunchBanner({ state, onRetry }: { state: LaunchState; onRetry: () => void }) {
  if (state.kind === 'idle') return null;
  if (state.kind === 'submitting') return <p className="quote-intake-banner quote-intake-banner--info" role="status">Saving quote intake step...</p>;
  if (state.kind === 'outage') {
    return <div className="quote-intake-banner quote-intake-banner--blocked" role="alert"><strong>Service outage fallback</strong><span>{state.message}</span><span>Support detail brw s01 local trace</span><button type="button" onClick={onRetry}>Retry intake</button></div>;
  }
  if (state.kind === 'blocked') {
    return <div className="quote-intake-banner quote-intake-banner--blocked" role="alert"><strong>Pipeline intake blocked</strong><span>{state.validation.message}</span></div>;
  }
  return <div className="quote-intake-banner quote-intake-banner--success" role="status"><strong>Pipeline run created</strong><span>Run ID: {state.launch.runId}</span><span>Next step: {state.launch.nextRoute ?? 'Review offers'}</span></div>;
}

export default QuoteIntakeFlow;

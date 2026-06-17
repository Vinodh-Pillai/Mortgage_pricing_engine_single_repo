import { type FocusEvent, type FormEvent, useEffect, useMemo, useRef, useState } from 'react';
import type { BorrowerIntake, LaunchState, MetadataState, ScenarioIntakeField } from '../../lib/api/quoteRuns';
import { CollapsibleSection } from './CollapsibleSection';
import { createDraftScenario, getDraftScenario, loadDraftBackup, saveDraftBackup, updateDraftScenario, validateDraftSection, type DraftBackup, type DraftScenario } from './draft';
import { launchQuoteRun } from './launch';
import { fieldsForStep, normalizeMetadataState, quoteIntakeSteps, type QuoteIntakeStepDefinition, type QuoteIntakeStepId } from './metadata';
import { ResumeDraft, draftToBackup } from './ResumeDraft';
import { StepFields } from './steps/StepFields';
import { errorsToValidation, firstInvalidField, validateFields, type IntakeFieldErrors } from './validation';
import './QuoteIntake.css';

export type PipelineIntakePageProps = {
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
const localUnsyncedDraftId = 'local-unsynced-pipeline-draft';

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

export function PipelineIntakePage({
  tenantId = tenantBoundaryPlaceholder,
  intake,
  errors = {},
  launchState,
  metadataState,
  onChange,
  onRetry,
  onSubmit,
  onNavigate,
  onEvidenceCapture,
}: PipelineIntakePageProps) {
  const [localIntake, setLocalIntake] = useState<BorrowerIntake>(intake ?? initialQuoteIntake);
  const [scenarioId, setScenarioId] = useState<string | null>(null);
  const [scenarioVersion, setScenarioVersion] = useState(0);
  const [localErrors, setLocalErrors] = useState<IntakeFieldErrors>({});
  const [expandedSections, setExpandedSections] = useState<Set<QuoteIntakeStepId>>(() => new Set([1]));
  const [flowState, setFlowState] = useState<LaunchState>(launchState ?? { kind: 'idle' });
  const [resumeBackup, setResumeBackup] = useState<DraftBackup | null>(() => loadDraftBackup(draftIdFromLocation() ?? undefined));
  const [resumeDismissed, setResumeDismissed] = useState(false);
  const [resumeLoading, setResumeLoading] = useState(false);
  const [resumeError, setResumeError] = useState('');
  const [statusMessage, setStatusMessage] = useState('');
  const [touchedFields, setTouchedFields] = useState<Set<keyof BorrowerIntake>>(() => new Set());
  const [validatedSections, setValidatedSections] = useState<Set<QuoteIntakeStepId>>(() => new Set());
  const [submitAttempted, setSubmitAttempted] = useState(false);
  const savingRef = useRef(false);
  const firstEntryDraftRef = useRef(false);
  const values = useMemo(() => normalizeDisplayedIntake(intake ?? localIntake), [intake, localIntake]);
  const mergedErrors = { ...errors, ...localErrors };
  const { metadata } = normalizeMetadataState(metadataState);
  const sections = useMemo(() => consolidateCanonicalFields(quoteIntakeSteps.map((step) => ({ step, fields: fieldsForStep(metadata, step.id) }))), [metadata]);
  const visibleErrors = useMemo(() => visibleValidationErrors(mergedErrors, sections, touchedFields, validatedSections, submitAttempted), [mergedErrors, sections, submitAttempted, touchedFields, validatedSections]);
  const draftId = draftIdFromLocation();

  useEffect(() => {
    if (intake) setLocalIntake(intake);
  }, [intake]);

  useEffect(() => {
    if (!draftId || resumeBackup || resumeDismissed) return;
    let cancelled = false;
    setResumeLoading(true);
    getDraftScenario(tenantId, draftId)
      .then((draft) => { if (!cancelled) setResumeBackup(draftToBackup(draft)); })
      .catch((error: unknown) => { if (!cancelled) setResumeError(error instanceof Error ? error.message : 'Draft scenario could not be loaded.'); })
      .finally(() => { if (!cancelled) setResumeLoading(false); });
    return () => { cancelled = true; };
  }, [draftId, resumeBackup, resumeDismissed, tenantId]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      if (hasAnyValue(values)) void saveDraft('autosave-interval');
    }, 30_000);
    return () => window.clearInterval(timer);
  }, [values, scenarioId, scenarioVersion]);

  function changeField(field: keyof BorrowerIntake, value: string) {
    const nextValues = { ...values, [field]: value };
    setLocalIntake(nextValues);
    setLocalErrors((current) => ({ ...current, [field]: undefined }));
    onChange?.(field, value);
    if (!firstEntryDraftRef.current && isLoanBasicsField(field) && value.trim()) {
      firstEntryDraftRef.current = true;
      void saveDraft('first-field-entry', nextValues);
    }
  }

  function toggleSection(step: QuoteIntakeStepDefinition) {
    const currentlyExpanded = step.id === 1 || expandedSections.has(step.id);
    const sectionErrors = currentlyExpanded ? validateSection(step.id) : {};
    setExpandedSections((current) => {
      const next = new Set(current);
      if (step.id === 1 || !currentlyExpanded || Object.keys(sectionErrors).length > 0) next.add(step.id);
      else next.delete(step.id);
      return next;
    });
  }

  function validateSection(stepId: QuoteIntakeStepId) {
    const fields = sectionForStep(stepId)?.fields ?? [];
    const sectionErrors = validateFields(fields, values);
    setValidatedSections((current) => new Set([...current, stepId]));
    setLocalErrors((current) => ({ ...current, ...sectionErrors }));
    if (Object.keys(sectionErrors).length > 0) {
      setFlowState({ kind: 'blocked', validation: errorsToValidation(sectionErrors, 'Complete the highlighted fields in this section.') });
      capture('section-validation-error', { section: sectionForStep(stepId)?.step.section, fields: Object.keys(sectionErrors) });
    }
    return sectionErrors;
  }

  async function saveDraft(reason = 'manual-save', valueOverride?: BorrowerIntake): Promise<DraftScenario | null> {
    if (savingRef.current) return null;
    savingRef.current = true;
    const draftValues = valueOverride ?? values;
    setFlowState({ kind: 'submitting' });
    try {
      const loanBasics = sectionForStep(1);
      if (!scenarioId && !resumeBackup?.scenarioId) {
        const draft = await createDraftScenario(tenantId, pickFields(draftValues, loanBasics?.fields ?? []));
        setScenarioId(draft.scenarioId);
        setScenarioVersion(draft.scenarioVersion);
        saveDraftBackup(draft.scenarioId, draft.scenarioVersion, 1, draftValues);
        setStatusMessage('Pipeline draft created and saved.');
        capture('draft-created', { scenarioId: draft.scenarioId, scenarioVersion: draft.scenarioVersion, reason });
        return draft;
      } else {
        const saved = await updateDraftScenario(tenantId, scenarioId ?? resumeBackup?.scenarioId ?? localUnsyncedDraftId, scenarioVersion || resumeBackup?.scenarioVersion || 1, 'scenario-identity', draftValues);
        setScenarioId(saved.scenarioId);
        setScenarioVersion(saved.scenarioVersion);
        saveDraftBackup(saved.scenarioId, saved.scenarioVersion, 1, draftValues);
        setStatusMessage('Pipeline draft auto-saved.');
        capture('draft-updated', { scenarioId: saved.scenarioId, scenarioVersion: saved.scenarioVersion, reason });
        return saved;
      }
    } catch (error: unknown) {
      saveDraftBackup(scenarioId ?? resumeBackup?.scenarioId ?? localUnsyncedDraftId, scenarioVersion || resumeBackup?.scenarioVersion || 0, 1, draftValues);
      const message = error instanceof Error ? error.message : 'Draft service is unavailable; local draft backup was stored.';
      setFlowState({ kind: 'outage', message });
      setStatusMessage(`${message} Local backup is available for resume.`);
      capture('draft-local-backup', { reason, message });
      return null;
    } finally {
      if (flowState.kind === 'submitting') setFlowState({ kind: 'idle' });
      savingRef.current = false;
    }
  }

  async function launch(event?: FormEvent<HTMLFormElement>) {
    event?.preventDefault();
    if (event) onSubmit?.(event);
    setSubmitAttempted(true);
    const validationErrors = sections.reduce((acc, { fields }) => ({ ...acc, ...validateFields(fields, values) }), {} as IntakeFieldErrors);
    if (Object.keys(validationErrors).length > 0) {
      setLocalErrors(validationErrors);
      setFlowState({ kind: 'blocked', validation: errorsToValidation(validationErrors, 'Complete required fields before launching quote.') });
      expandFirstInvalidSection(validationErrors);
      focusFirstInvalid(validationErrors);
      capture('quote-launch-validation-error', { fields: Object.keys(validationErrors) });
      return;
    }

    setFlowState({ kind: 'submitting' });
    try {
      const savedDraft = await saveDraft('pre-launch');
      const resolvedScenarioId = savedDraft?.scenarioId ?? scenarioId ?? resumeBackup?.scenarioId;
      if (!resolvedScenarioId) throw new Error('Connected scenario draft is unavailable; quote launch requires backend draft support.');
      const resolvedVersion = savedDraft?.scenarioVersion ?? (scenarioVersion || resumeBackup?.scenarioVersion || 1);
      const validation = await validateDraftSection(tenantId, resolvedScenarioId, resolvedVersion, 'preferences');
      if (!validation.passed) {
        setLocalErrors(validation.blockers);
        setFlowState({ kind: 'blocked', validation });
        expandFirstInvalidSection(validation.blockers);
        capture('quote-launch-blocked', { blockers: Object.keys(validation.blockers) });
        return;
      }
      const launched = await launchQuoteRun(tenantId, resolvedScenarioId, resolvedVersion, values);
      if (launched.kind === 'blocked') {
        setLocalErrors(launched.validation.blockers);
        setFlowState({ kind: 'blocked', validation: launched.validation });
        expandFirstInvalidSection(launched.validation.blockers);
        capture('quote-launch-blocked', { blockers: launched.blockers });
        return;
      }
      if (launched.kind === 'needs-attention') {
        setFlowState({ kind: 'blocked', validation: { passed: false, status: 'BLOCKED', message: launched.message, blockers: {} } });
        setStatusMessage(launched.message);
        capture('quote-launch-needs-attention', { blockers: launched.blockers });
        return;
      }
      setFlowState({ kind: 'created', launch: launched.launch });
      capture('quote-launch-created', { runId: launched.launch.runId, nextRoute: launched.launch.nextRoute });
      if (launched.launch.nextRoute) onNavigate?.(launched.launch.nextRoute);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Quote launch is temporarily unavailable.';
      setFlowState({ kind: 'outage', message });
      setStatusMessage(message);
      capture('quote-launch-outage', { message });
    }
  }

  function resumeDraft() {
    if (!resumeBackup) return;
    setScenarioId(resumeBackup.scenarioId);
    setScenarioVersion(resumeBackup.scenarioVersion);
    const resumedIntake = normalizeDisplayedIntake({ ...values, ...resumeBackup.intake });
    setLocalIntake(resumedIntake);
    Object.entries(resumedIntake).forEach(([field, value]) => {
      if (typeof value === 'string') onChange?.(field as keyof BorrowerIntake, value);
    });
    setExpandedSections(new Set([1, 6]));
    setResumeDismissed(true);
    setStatusMessage(`Draft ${resumeBackup.scenarioId} resumed.`);
  }

  function handleBlur(event: FocusEvent<HTMLFormElement>) {
    const target = event.target as unknown as HTMLInputElement | HTMLTextAreaElement;
    const fieldName = target.name as keyof BorrowerIntake | undefined;
    const field = fieldName ? fieldForId(sections, fieldName) : undefined;
    if (fieldName && field) {
      setTouchedFields((current) => new Set([...current, fieldName]));
      const fieldErrors = validateFields([field], values);
      setLocalErrors((current) => ({ ...current, [fieldName]: fieldErrors[fieldName] }));
    }
    if (target.name && target.value.trim()) void saveDraft('field-blur');
  }

  function sectionForStep(stepId: QuoteIntakeStepId) {
    return sections.find((candidate) => candidate.step.id === stepId);
  }

  function expandFirstInvalidSection(nextErrors: IntakeFieldErrors) {
    const invalidField = firstInvalidField(nextErrors);
    const invalidStep = sections.find(({ fields }) => fields.some((field) => field.fieldId === invalidField))?.step.id;
    if (invalidStep) setExpandedSections((current) => new Set([...current, invalidStep]));
  }

  function focusFirstInvalid(nextErrors: IntakeFieldErrors) {
    const invalid = firstInvalidField(nextErrors);
    if (invalid) window.setTimeout(() => document.getElementById(invalid)?.focus(), 0);
  }

  function capture(action: string, detail: Record<string, unknown>) {
    onEvidenceCapture?.({ action, storyId: 'PII-26-S14', ...detail });
  }

  return (
    <section id="borrower-intake" className="quote-intake-shell quote-intake-shell--single-page" aria-labelledby="intake-heading">
      <div className="quote-intake-hero quote-intake-hero--sticky">
        <div>
          <p className="eyebrow">Pipeline</p>
          <h2 id="intake-heading">Pipeline Intake</h2>
          <p>Complete the loan application in one scrollable page. Loan Basics stays open; every other section can collapse when not needed.</p>
        </div>
        <div className="quote-intake-hero__actions" aria-label="Pipeline actions">
          <button type="button" onClick={() => void saveDraft('manual-save')} disabled={flowState.kind === 'submitting'}>Save Draft</button>
          <button type="button" className="quote-intake-primary" onClick={() => void launch()} disabled={flowState.kind === 'submitting'}>{flowState.kind === 'submitting' ? 'Working...' : 'Launch Quote'}</button>
        </div>
      </div>

      <ResumeDraft draftId={draftId} backup={resumeDismissed ? null : resumeBackup} loading={resumeLoading} error={resumeError} onResume={resumeDraft} onDismiss={() => setResumeDismissed(true)} />
      <LaunchBanner state={flowState} onRetry={() => { setFlowState({ kind: 'idle' }); onRetry?.(); }} />
      {statusMessage ? <p className="quote-intake-status" role="status">{statusMessage}</p> : null}

      <form className="quote-intake-form quote-intake-form--single-page" onSubmit={launch} onBlur={handleBlur} noValidate>
        {sections.map(({ step, fields }) => {
          const expanded = step.id === 1 || expandedSections.has(step.id);
          const hasErrors = fields.some((field) => Boolean(visibleErrors[field.fieldId]));
          return (
            <CollapsibleSection
              key={step.id}
              id={`pipeline-section-${step.id}`}
              title={step.label}
              summary={step.summary}
              expanded={expanded}
              alwaysExpanded={step.id === 1}
              hasErrors={hasErrors}
              onToggle={() => toggleSection(step)}
            >
              <StepFields fields={fields} intake={values} errors={visibleErrors} onChange={changeField} />
            </CollapsibleSection>
          );
        })}
        <div className="quote-intake-actions quote-intake-actions--bottom" aria-label="Pipeline launch actions">
          <button type="button" onClick={() => void saveDraft('manual-save')} disabled={flowState.kind === 'submitting'}>Save Draft</button>
          <button type="submit" disabled={flowState.kind === 'submitting'}>{flowState.kind === 'submitting' ? 'Launching quote...' : 'Launch Quote'}</button>
        </div>
      </form>
    </section>
  );
}

function pickFields(values: BorrowerIntake, fields: ScenarioIntakeField[]) {
  return fields.reduce((acc, field) => {
    acc[field.fieldId] = values[field.fieldId];
    return acc;
  }, {} as Partial<BorrowerIntake>);
}

function isLoanBasicsField(field: keyof BorrowerIntake) {
  return ['loanPurpose', 'loanAmount', 'purchasePriceOrValue', 'propertyType', 'propertyZip', 'occupancyType', 'unitCount'].includes(field);
}

function normalizeDisplayedIntake(input: BorrowerIntake): BorrowerIntake {
  if (input.purchasePrice !== '-1' && input.purchasePriceOrValue !== '-1') return input;
  return {
    ...input,
    purchasePrice: input.purchasePrice === '-1' ? '' : input.purchasePrice,
    purchasePriceOrValue: input.purchasePriceOrValue === '-1' ? '' : input.purchasePriceOrValue,
  };
}

function consolidateCanonicalFields(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>) {
  const seenCanonical = new Set<keyof BorrowerIntake>();
  const canonicalSingleSurfaceFields = new Set<keyof BorrowerIntake>(['propertyZip']);
  return sections.map(({ step, fields }) => ({
    step,
    fields: fields.filter((field) => {
      if (!canonicalSingleSurfaceFields.has(field.fieldId)) return true;
      if (seenCanonical.has(field.fieldId)) return false;
      seenCanonical.add(field.fieldId);
      return true;
    }),
  }));
}

function hasAnyValue(values: BorrowerIntake) {
  return Object.values(values).some((value) => value.trim());
}

function fieldForId(sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>, fieldId: keyof BorrowerIntake) {
  return sections.flatMap(({ fields }) => fields).find((field) => field.fieldId === fieldId);
}

function visibleValidationErrors(
  errors: IntakeFieldErrors,
  sections: Array<{ step: QuoteIntakeStepDefinition; fields: ScenarioIntakeField[] }>,
  touchedFields: Set<keyof BorrowerIntake>,
  validatedSections: Set<QuoteIntakeStepId>,
  submitAttempted: boolean,
) {
  if (submitAttempted) return errors;
  return sections.reduce((visible, { step, fields }) => {
    for (const field of fields) {
      if ((touchedFields.has(field.fieldId) || validatedSections.has(step.id)) && errors[field.fieldId]) {
        visible[field.fieldId] = errors[field.fieldId];
      }
    }
    return visible;
  }, {} as IntakeFieldErrors);
}

function draftIdFromLocation() {
  if (typeof window === 'undefined') return null;
  const params = new URLSearchParams(window.location.search);
  return params.get('draft') ?? params.get('scenarioId');
}

function LaunchBanner({ state, onRetry }: { state: LaunchState; onRetry: () => void }) {
  if (state.kind === 'idle') return null;
  if (state.kind === 'submitting') return <p className="quote-intake-banner quote-intake-banner--info" role="status">Saving pipeline intake...</p>;
  if (state.kind === 'outage') {
    return <div className="quote-intake-banner quote-intake-banner--blocked" role="alert"><strong>Pipeline service fallback</strong><span>{state.message}</span><button type="button" onClick={onRetry}>Retry</button></div>;
  }
  if (state.kind === 'blocked') {
    return <div className="quote-intake-banner quote-intake-banner--blocked" role="alert"><strong>Pipeline intake blocked</strong><span>{state.validation.message}</span></div>;
  }
  return <div className="quote-intake-banner quote-intake-banner--success" role="status"><strong>Pipeline run created</strong><span>Run ID: {state.launch.runId}</span><span>Next step: {state.launch.nextRoute ?? 'Review offers'}</span></div>;
}

export default PipelineIntakePage;

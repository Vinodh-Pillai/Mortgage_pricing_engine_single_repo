import { type FormEvent } from 'react';
import { MortgageInput } from '../../components/MortgageInput';
import { ChipList } from '../../components/ChipList';
import type {
  BorrowerIntake,
  LaunchState,
  MetadataState,
  ScenarioIntakeMetadata,
  ProgressiveQuickQuoteState,
  ScenarioIntakeField,
} from '../../lib/api/quoteRuns';
import { Step1DraftScenario } from './steps/Step1DraftScenario';
import { Step2BorrowerCredit } from './steps/Step2BorrowerCredit';
import { Step3LoanStructure } from './steps/Step3LoanStructure';
import { Step4Property } from './steps/Step4Property';
import { Step5IncomeAssets } from './steps/Step5IncomeAssets';
import { Step6ProductLock } from './steps/Step6ProductLock';

const uiTraceId = 'brw-s01-local-trace';

export default function QuickQuoteIntake({
  intake,
  errors,
  launchState,
  metadataState,
  onChange,
  onRetry,
  onSubmit,
}: {
  intake: BorrowerIntake;
  errors: Partial<Record<keyof BorrowerIntake, string>>;
  launchState: LaunchState;
  metadataState: MetadataState;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
  onRetry: () => void;
  onSubmit: (event: FormEvent<HTMLFormElement>) => void;
}) {
  const quickQuoteState = metadataState.kind === 'loaded' ? metadataState.metadata.quickQuoteState : undefined;
  return (
    <section id="borrower-intake" className="panel quick-quote-module" aria-labelledby="intake-heading">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Guided quick quote</p>
          <h2 id="intake-heading">Progressive quick quote intake</h2>
        </div>
        {launchState.kind === 'outage' ? null : <DiagnosticsDetails items={[`Assisted quote intake session ${uiTraceId}`]} />}
      </div>

      <p className="field-help">
        Capture the borrower, credit, loan, property, income, product preference, lock timing, and business intent facts needed
        for a pricing scenario. The workbench records facts only and does not infer rates, thresholds, or eligibility decisions.
      </p>
      <LaunchBanner state={launchState} onRetry={onRetry} />
      <ScenarioIntakeMetadataPanel state={metadataState} />
      <QuickQuoteStatePanel state={quickQuoteState} />

      <form className="intake-form quick-quote-form" onSubmit={onSubmit} noValidate>
        <Step1DraftScenario intake={intake} errors={errors} onChange={onChange} />
        <Step2BorrowerCredit intake={intake} errors={errors} onChange={onChange} />
        <Step3LoanStructure intake={intake} errors={errors} onChange={onChange} />
        <Step4Property intake={intake} errors={errors} onChange={onChange} />
        <Step5IncomeAssets intake={intake} errors={errors} onChange={onChange} />
        <Step6ProductLock intake={intake} errors={errors} onChange={onChange} />

        {metadataState.kind === 'loaded' ? <AdvancedScenarioIntake metadata={metadataState.metadata} intake={intake} errors={errors} onChange={onChange} /> : null}

        <button type="submit" disabled={launchState.kind === 'submitting'}>
          {launchState.kind === 'submitting' ? 'Starting quote...' : 'Start quick quote'}
        </button>
      </form>
    </section>
  );
}

function QuickQuoteStatePanel({ state }: { state?: ProgressiveQuickQuoteState }) {
  if (!state) return null;
  return (
    <div className="quick-quote-state" aria-label="Progressive quick quote setup status">
      <div>
        <strong>Minimal first step</strong>
        <ChipList label="Minimal first step fields" values={state.minimalFirstStepFields.map(businessFacingText)} />
      </div>
      <div>
        <strong>Progressive sections</strong>
        <ChipList label="Progressive quick quote sections" values={state.progressiveSectionOrder.map(businessFacingText)} />
      </div>
      <div className="field-group--full">
        <strong>Needed scenario facts and attention state</strong>
        <p className="field-help">{businessFacingText(state.fallbackReason)}</p>
        <ChipList label="Quote facts still needed" values={state.quoteServiceRequiredFacts.map(businessFacingText)} />
        <ChipList label="Authoritative fact sources" values={state.backendOwnedFactSources.map(businessFacingText)} />
        <ChipList label="Setup items needing attention" values={state.blockedByContracts.map(businessFacingText)} />
      </div>
    </div>
  );
}

function LaunchBanner({ state, onRetry }: { state: LaunchState; onRetry: () => void }) {
  if (state.kind === 'idle') return null;
  if (state.kind === 'submitting') {
    return <p className="banner banner--info" role="status">Starting quote run...</p>;
  }
  if (state.kind === 'blocked') {
    return (
      <div className="banner banner--blocked" role="alert">
        <strong>Quick quote intake blocked</strong>
        <span>{state.validation.message}</span>
        <ChipList label="Fields needing attention" values={Object.entries(state.validation.blockers).map(([field, message]) => `${field}: ${message}`)} />
        <button type="button" onClick={onRetry}>Reset and try again</button>
      </div>
    );
  }
  if (state.kind === 'created') {
    return (
      <div className="banner banner--success" role="status">
        <strong>Quick quote run created</strong>
        <span>Run ID: {state.launch.runId}</span>
        <span>Next step: {state.launch.nextRoute ?? 'Review offers'}</span>
      </div>
    );
  }
  if (state.kind === 'outage') {
    return <p className="banner banner--blocked" role="alert">Service outage: {state.message}</p>;
  }
  return null;
}

function ScenarioIntakeMetadataPanel({ state }: { state: MetadataState }) {
  if (state.kind === 'loading') {
    return <p className="banner banner--info" role="status">Loading scenario intake guidance...</p>;
  }

  if (state.kind === 'unreachable') {
    return <p className="banner banner--blocked" role="alert">{state.message}</p>;
  }

  const metadata = state.metadata;
  return (
    <div className="scenario-metadata-panel" aria-label="Scenario intake setup guidance">
      <dl className="status-grid">
        <dt>Setup source</dt><dd>{businessFacingText(metadata.dependencyStatus)}</dd>
        <dt>Review package</dt><dd>{businessFacingText(metadata.auditPackageId)}</dd>
        <dt>Review reference</dt><dd>{businessFacingText(metadata.replayHashRef)}</dd>
      </dl>
      <ChipList label="Decision-quality controls" values={metadata.decisionControls.map(businessFacingText)} />
      <ChipList label="Scenario intake items needing attention" values={metadata.validationIssues.map((issue) => `${issue.severity}: ${issue.message}`).map(businessFacingText)} />
      <p className="field-help">{businessFacingText(metadata.fallbackReason)}</p>
    </div>
  );
}

function AdvancedScenarioIntake({
  metadata,
  intake,
  errors,
  onChange,
}: {
  metadata: ScenarioIntakeMetadata;
  intake: BorrowerIntake;
  errors: Partial<Record<keyof BorrowerIntake, string>>;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
}) {
  return (
    <details className="advanced-intake" open>
      <summary>Additional mortgage scenario facts</summary>
      <div className="advanced-intake__groups">
        {metadata.fieldGroups.map((group) => (
          <fieldset key={group.groupId} className="advanced-intake__group">
            <legend>{group.label}</legend>
            <p className="field-help">{group.helpText}</p>
            {group.fields.map((field) => <MetadataField key={field.fieldId} field={field} intake={intake} error={errors[field.fieldId]} onChange={onChange} />)}
          </fieldset>
        ))}
      </div>
    </details>
  );
}

function MetadataField({
  field,
  intake,
  error,
  onChange,
}: {
  field: ScenarioIntakeField;
  intake: BorrowerIntake;
  error?: string;
  onChange: (field: keyof BorrowerIntake, value: string) => void;
}) {
  const errorId = `${field.fieldId}-metadata-error`;
  const helpId = `${field.fieldId}-metadata-help`;
  return (
    <div className={field.dataType === 'textarea' ? 'field-group field-group--full' : 'field-group'}>
      <label htmlFor={field.fieldId}>{field.label} {field.required ? <span aria-hidden="true">*</span> : null}</label>
      {field.dataType === 'textarea' ? (
        <textarea id={field.fieldId} value={intake[field.fieldId]} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : helpId} onChange={(event) => onChange(field.fieldId, event.target.value)} />
      ) : (
        <input id={field.fieldId} type={field.dataType === 'email' ? 'email' : field.dataType === 'number' ? 'number' : 'text'} value={intake[field.fieldId]} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : helpId} onChange={(event) => onChange(field.fieldId, event.target.value)} />
      )}
      <p id={helpId} className="field-help">{businessFacingText(field.helpText)} Source: {businessFacingText(field.sourceRef)}. Readiness: {businessFacingText(field.decisionQuality)}.</p>
      <ChipList label={`${field.fieldId} validation messages`} values={field.validationMessages.map(businessFacingText)} />
      {error ? <p id={errorId} role="alert">{error}</p> : null}
    </div>
  );
}

function DiagnosticsDetails({ items }: { items: string[] }) {
  if (!items.length) return null;
  return (
    <details className="trace-badge">
      <summary>Support details</summary>
      <ul>{items.map((item) => <li key={item}>{businessFacingText(item)}</li>)}</ul>
    </details>
  );
}

function serviceReadinessText(value: string | null | undefined) {
  if (!value) return 'Not provided';
  return 'Configuration needed before live service use.';
}

function businessFacingText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not provided';
  return String(value)
    .replace(/NO_UPSTREAMS_CONFIGURED/gi, 'Connected services need setup')
    .replace(/FALLBACK_STATIC_DEPENDENCIES_UNAVAILABLE/gi, 'Configuration details need setup')
    .replace(/Support reference/gi, 'Support detail')
    .replace(/validation_result\.json|validation_trace\.jsonl|module_evidence_index\.json|blocker_register\.json/gi, 'review package item')
    .replace(/ui_trace_id|uiTraceId|trace id|trace refs?|correlation id/gi, 'support reference')
    .replace(/deployment disabled|deployment-disabled/gi, 'live action unavailable')
    .replace(/release readiness|release candidate readiness|release candidate/gi, 'business readiness')
    .replace(/contract conformance blocker/gi, 'setup review item')
    .replace(/evidence set completeness|completeness status/gi, 'review package completeness')
    .replace(/quality owner signature required|release owner signature required|owner signature required/gi, 'owner review required')
    .replace(/smoke check: configured result required|schema compatibility: blocked|config validation: pending|policy signatures: required/gi, 'setup review required')
    .replace(/adapter boundary|adapter status|adapter/gi, 'service connection')
    .replace(/backend[- ]owned/gi, 'authoritative')
    .replace(/backend/gi, 'connected service')
    .replace(/blockers?/gi, 'items needing attention')
    .replace(/blocked/gi, 'needs attention')
    .replace(/evidence/gi, 'review record')
    .replace(/audit/gi, 'review')
    .replace(/replay hash|replay/gi, 'review reference')
    .replace(/hash/gi, 'processing record')
    .replace(/integrity/gi, 'processing check')
    .replace(/rounding trace/gi, 'rounding review')
    .replace(/BFF/gi, 'workbench service')
    .replace(/Configured upstream/gi, 'Configured service')
    .replace(/upstream/gi, 'configured service')
    .replace(/downstream/gi, 'connected workflow')
    .replace(/SLA contract required/gi, 'Response target needs setup')
    .replace(/Awaiting configured SLA contract/gi, 'Response target needs setup')
    .replace(/SLA deadline supplied by configured privacy service/gi, 'Response target supplied by configured privacy service')
    .replace(/DLQ/gi, 'exception queue')
    .replace(/DSAR/gi, 'privacy request')
    .replace(/RBAC/gi, 'role access')
    .replace(/drift/gi, 'change')
    .replace(/override ledger/gi, 'change history')
    .replace(/policy[- ]?version/gi, 'guidance version')
    .replace(/policy digest/gi, 'guidance summary')
    .replace(/market-rule/gi, 'market guidance')
    .replace(/release gates?/gi, 'readiness checks')
    .replace(/dependency status/gi, 'setup status')
    .replace(/route/gi, 'path')
    .replace(/contract/gi, 'setup')
    .replace(/[_:./-]+/g, ' ')
    .replace(/_/g, ' ')
    .replace(/\s+/g, ' ')
    .trim();
}

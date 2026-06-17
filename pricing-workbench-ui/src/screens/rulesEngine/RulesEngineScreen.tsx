import { useMemo, useState, type FormEvent } from 'react';

type RuleCategory = 'adjustments' | 'margins' | 'exceptions' | 'customFields' | 'governance';
type RuleStatus = 'ACTIVE' | 'DRAFT' | 'REVIEW' | 'DISABLED';

type RuleRecord = {
  id: string;
  category: RuleCategory;
  name: string;
  field: string;
  operator: string;
  value: string;
  priority: number;
  status: RuleStatus;
  source: string;
  notes: string;
};

type RuleDraft = Pick<RuleRecord, 'name' | 'field' | 'operator' | 'value' | 'priority' | 'status' | 'notes'>;

const categories: { id: RuleCategory; label: string; capability: string; summary: string }[] = [
  { id: 'adjustments', label: 'Adjustment Rules', capability: 'adjustment-service', summary: 'Name, field, operator, value, priority, status.' },
  { id: 'margins', label: 'Margin Rules', capability: 'margin-service', summary: 'Company policy, SRP, overlays, profitability.' },
  { id: 'exceptions', label: 'Exception Rules', capability: 'exception-concessions', summary: 'Concessions, authority matrix, manual price guard.' },
  { id: 'customFields', label: 'Custom Fields', capability: 'custom-rules workbench', summary: 'Definitions, validation, UI hints.' },
  { id: 'governance', label: 'Governance', capability: 'custom-rules workbench', summary: 'Lifecycle, pending review, dynamic rules.' },
];

const fieldsByCategory: Record<RuleCategory, string[]> = {
  adjustments: ['loan.attribute.ref', 'product.attribute.ref', 'lock.attribute.ref', 'channel.attribute.ref'],
  margins: ['companyPolicy.ref', 'srp.ref', 'overlay.ref', 'profitability.ref'],
  exceptions: ['concession.type.ref', 'authority.level.ref', 'priceGuard.reason.ref', 'manualReview.ref'],
  customFields: ['field.key', 'field.type', 'validation.ref', 'uiHint.ref'],
  governance: ['lifecycle.state', 'review.queue.ref', 'dynamicRule.ref', 'audit.event.ref'],
};

const operators = ['equals', 'not equals', 'contains', 'greater than', 'less than', 'in list', 'requires review'];

const initialRules: RuleRecord[] = [
  { id: 'adj-1', category: 'adjustments', name: 'Adjustment matrix routing', field: 'loan.attribute.ref', operator: 'in list', value: 'configured-adjustment-bucket-ref', priority: 10, status: 'ACTIVE', source: 'adjustment-service', notes: 'Displays backend-owned adjustment references only.' },
  { id: 'adj-2', category: 'adjustments', name: 'Product adjustment overlay', field: 'product.attribute.ref', operator: 'equals', value: 'eligible-product-ref', priority: 20, status: 'DRAFT', source: 'adjustment-service', notes: 'No rate, fee, or LLPA value is defined in UI.' },
  { id: 'margin-1', category: 'margins', name: 'Company policy margin', field: 'companyPolicy.ref', operator: 'equals', value: 'active-policy-ref', priority: 5, status: 'ACTIVE', source: 'margin-service', notes: 'Policy values remain backend-owned.' },
  { id: 'margin-2', category: 'margins', name: 'SRP profitability overlay', field: 'profitability.ref', operator: 'requires review', value: 'profitability-review-ref', priority: 15, status: 'REVIEW', source: 'margin-service', notes: 'Routes to governance review when configured.' },
  { id: 'exc-1', category: 'exceptions', name: 'Concession authority matrix', field: 'authority.level.ref', operator: 'in list', value: 'approved-authority-ref', priority: 1, status: 'ACTIVE', source: 'exception-concessions', notes: 'Authority matrix reference, not a local approval rule.' },
  { id: 'exc-2', category: 'exceptions', name: 'Manual price guard', field: 'priceGuard.reason.ref', operator: 'requires review', value: 'manual-guard-review-ref', priority: 2, status: 'REVIEW', source: 'exception-concessions', notes: 'Blocks unsourced price mutation attempts.' },
  { id: 'cf-1', category: 'customFields', name: 'Custom field definition', field: 'field.key', operator: 'equals', value: 'tenant-defined-field-ref', priority: 30, status: 'DRAFT', source: 'custom-rules workbench', notes: 'Validation and UI hints point to configured references.' },
  { id: 'gov-1', category: 'governance', name: 'Pending review lifecycle', field: 'review.queue.ref', operator: 'contains', value: 'pending-review-ref', priority: 1, status: 'REVIEW', source: 'custom-rules workbench', notes: 'Lifecycle evidence for draft, simulate, approve, publish.' },
];

const emptyDraft: RuleDraft = {
  name: '',
  field: fieldsByCategory.adjustments[0],
  operator: operators[0],
  value: '',
  priority: 50,
  status: 'DRAFT',
  notes: '',
};

export const rulesEngineEvidenceTarget = '.local-harness/evidence/direct-rules-engine-screen/rules-engine.json';
export const rulesEngineStateCoverage = ['adjustment-rules', 'margin-rules', 'exception-rules', 'custom-fields', 'governance', 'add-rule-slide-over', 'bulk-actions', 'inline-editing'];

export function RulesEngineScreen() {
  const [rules, setRules] = useState<RuleRecord[]>(initialRules);
  const [activeCategory, setActiveCategory] = useState<RuleCategory>('adjustments');
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [draft, setDraft] = useState<RuleDraft>(emptyDraft);
  const [search, setSearch] = useState('');

  const category = categories.find((item) => item.id === activeCategory) ?? categories[0];
  const categoryRules = useMemo(() => {
    const normalizedSearch = search.trim().toLowerCase();
    return rules.filter((rule) => rule.category === activeCategory && (!normalizedSearch || [rule.name, rule.field, rule.operator, rule.value, rule.status, rule.source].some((value) => String(value).toLowerCase().includes(normalizedSearch))));
  }, [activeCategory, rules, search]);
  const selectedRules = rules.filter((rule) => selectedIds.includes(rule.id));
  const metrics = useMemo(() => ({
    total: rules.length,
    active: rules.filter((rule) => rule.status === 'ACTIVE').length,
    review: rules.filter((rule) => rule.status === 'REVIEW').length,
    draft: rules.filter((rule) => rule.status === 'DRAFT').length,
  }), [rules]);

  function openAddDrawer() {
    setDraft({ ...emptyDraft, field: fieldsByCategory[activeCategory][0] });
    setDrawerOpen(true);
  }

  function updateRule(ruleId: string, patch: Partial<RuleRecord>) {
    setRules((current) => current.map((rule) => rule.id === ruleId ? { ...rule, ...patch } : rule));
  }

  function toggleSelected(ruleId: string) {
    setSelectedIds((current) => current.includes(ruleId) ? current.filter((id) => id !== ruleId) : [...current, ruleId]);
  }

  function setBulkStatus(status: RuleStatus) {
    setRules((current) => current.map((rule) => selectedIds.includes(rule.id) ? { ...rule, status } : rule));
  }

  function addRule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const name = draft.name.trim();
    const value = draft.value.trim();
    if (!name || !value) return;
    const nextRule: RuleRecord = {
      id: `${activeCategory}-${Date.now()}`,
      category: activeCategory,
      name,
      field: draft.field,
      operator: draft.operator,
      value,
      priority: Number.isFinite(draft.priority) ? draft.priority : 50,
      status: draft.status,
      source: category.capability,
      notes: draft.notes.trim() || 'Local draft; backend owns persisted rule values and pricing policy.',
    };
    setRules((current) => [nextRule, ...current]);
    setSelectedIds([nextRule.id]);
    setDrawerOpen(false);
  }

  return (
    <div className="rules-engine" aria-labelledby="rules-engine-title">
      <style>{rulesEngineStyles}</style>
      <aside className="rules-engine__sidebar glass-card" aria-label="Rule categories">
        <span className="rules-engine__logo">RE</span>
        {categories.map((item) => (
          <button key={item.id} type="button" aria-pressed={activeCategory === item.id} onClick={() => { setActiveCategory(item.id); setSelectedIds([]); }}>
            <strong>{item.label}</strong>
            <small>{item.capability}</small>
          </button>
        ))}
      </aside>

      <main className="rules-engine__main">
        <header className="rules-engine__hero glass-card">
          <div>
            <p className="rules-engine__eyebrow">adjustment-service / margin-service / exception-concessions / custom-rules workbench</p>
            <h2 id="rules-engine-title">Rules Engine &amp; Custom Fields</h2>
            <p>Manage pricing rule references, adjustments, exceptions, custom fields, lifecycle review, and dynamic rule drafts without embedding pricing constants in the UI.</p>
          </div>
          <div className="rules-engine__hero-actions">
            <button type="button" className="primary" onClick={openAddDrawer}>Add rule</button>
            <button type="button" onClick={() => setBulkStatus('REVIEW')} disabled={selectedIds.length === 0}>Send to review</button>
          </div>
        </header>

        <section className="rules-engine__metrics" aria-label="Rules summary">
          <Metric label="Rules" value={metrics.total} />
          <Metric label="Active" value={metrics.active} />
          <Metric label="Review" value={metrics.review} />
          <Metric label="Draft" value={metrics.draft} />
        </section>

        <section className="rules-engine__workspace glass-card" aria-labelledby="active-rules-heading">
          <div className="rules-engine__toolbar">
            <div>
              <p className="rules-engine__eyebrow">{category.capability}</p>
              <h3 id="active-rules-heading">{category.label}</h3>
              <p>{category.summary}</p>
            </div>
            <div className="rules-engine__filters" role="search" aria-label="Rule filters">
              <input value={search} onChange={(event) => setSearch(event.target.value)} placeholder="Search rules" />
              <button type="button" onClick={openAddDrawer}>Add</button>
            </div>
          </div>

          <BulkActions selectedRules={selectedRules} onActivate={() => setBulkStatus('ACTIVE')} onDraft={() => setBulkStatus('DRAFT')} onReview={() => setBulkStatus('REVIEW')} onDisable={() => setBulkStatus('DISABLED')} />

          <RuleBuilderPreview category={category.label} field={draft.field || fieldsByCategory[activeCategory][0]} operator={draft.operator} value={draft.value || 'value-ref'} />

          <div className="rules-engine__grid" role="table" aria-label={`${category.label} grid`}>
            <div role="row" className="rules-engine__row rules-engine__row--head">
              <span role="columnheader">Select</span>
              <span role="columnheader">Name</span>
              <span role="columnheader">Field</span>
              <span role="columnheader">Operator</span>
              <span role="columnheader">Value</span>
              <span role="columnheader">Priority</span>
              <span role="columnheader">Status</span>
              <span role="columnheader">Source</span>
            </div>
            {categoryRules.length === 0 ? <div role="row" className="rules-engine__row"><span role="cell">No rules match this category and search.</span></div> : null}
            {categoryRules.map((rule) => <RuleRow key={rule.id} rule={rule} selected={selectedIds.includes(rule.id)} fields={fieldsByCategory[activeCategory]} onToggle={() => toggleSelected(rule.id)} onUpdate={(patch) => updateRule(rule.id, patch)} />)}
          </div>
        </section>
      </main>

      {drawerOpen ? <AddRuleDrawer category={category.label} fields={fieldsByCategory[activeCategory]} draft={draft} onDraftChange={setDraft} onClose={() => setDrawerOpen(false)} onSubmit={addRule} /> : null}
    </div>
  );
}

function RuleRow({ rule, selected, fields, onToggle, onUpdate }: { rule: RuleRecord; selected: boolean; fields: string[]; onToggle: () => void; onUpdate: (patch: Partial<RuleRecord>) => void }) {
  return (
    <div role="row" className="rules-engine__row">
      <span role="cell"><input type="checkbox" aria-label={`Select ${rule.name}`} checked={selected} onChange={onToggle} /></span>
      <span role="cell"><input value={rule.name} onChange={(event) => onUpdate({ name: event.target.value })} aria-label={`${rule.name} name`} /></span>
      <span role="cell"><select value={rule.field} onChange={(event) => onUpdate({ field: event.target.value })} aria-label={`${rule.name} field`}>{fields.map((field) => <option key={field} value={field}>{field}</option>)}</select></span>
      <span role="cell"><select value={rule.operator} onChange={(event) => onUpdate({ operator: event.target.value })} aria-label={`${rule.name} operator`}>{operators.map((operator) => <option key={operator} value={operator}>{operator}</option>)}</select></span>
      <span role="cell"><input value={rule.value} onChange={(event) => onUpdate({ value: event.target.value })} aria-label={`${rule.name} value`} /></span>
      <span role="cell"><input type="number" min="1" value={rule.priority} onChange={(event) => onUpdate({ priority: Number(event.target.value) })} aria-label={`${rule.name} priority`} /></span>
      <span role="cell"><select value={rule.status} onChange={(event) => onUpdate({ status: event.target.value as RuleStatus })} aria-label={`${rule.name} status`}><option>ACTIVE</option><option>DRAFT</option><option>REVIEW</option><option>DISABLED</option></select></span>
      <span role="cell"><span className={`rules-engine__status rules-engine__status--${statusClass(rule.status)}`}>{rule.status}</span><small>{rule.source}</small></span>
    </div>
  );
}

function BulkActions({ selectedRules, onActivate, onDraft, onReview, onDisable }: { selectedRules: RuleRecord[]; onActivate: () => void; onDraft: () => void; onReview: () => void; onDisable: () => void }) {
  const disabled = selectedRules.length === 0;
  return (
    <div className="rules-engine__bulk" aria-label="Bulk actions">
      <strong>{selectedRules.length} selected</strong>
      <button type="button" disabled={disabled} onClick={onActivate}>Activate</button>
      <button type="button" disabled={disabled} onClick={onDraft}>Draft</button>
      <button type="button" disabled={disabled} onClick={onReview}>Review</button>
      <button type="button" disabled={disabled} onClick={onDisable}>Disable</button>
      <span>{selectedRules.map((rule) => rule.name).join(', ') || 'No selection'}</span>
    </div>
  );
}

function RuleBuilderPreview({ category, field, operator, value }: { category: string; field: string; operator: string; value: string }) {
  return (
    <div className="rules-engine__builder" aria-label="Visual condition builder">
      <span>{category}</span>
      <strong>{field}</strong>
      <em>{operator}</em>
      <strong>{value}</strong>
    </div>
  );
}

function AddRuleDrawer({ category, fields, draft, onDraftChange, onClose, onSubmit }: { category: string; fields: string[]; draft: RuleDraft; onDraftChange: (draft: RuleDraft) => void; onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  const categorySlug = category.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
  const fieldId = `${categorySlug}-field`;
  const operatorId = `${categorySlug}-operator`;
  const valueId = `${categorySlug}-value`;
  return (
    <div className="rules-engine__overlay" role="presentation">
      <aside className="rules-engine__drawer glass-card" role="dialog" aria-modal="true" aria-labelledby="add-rule-title">
        <header className="rules-engine__drawer-head">
          <div><p className="rules-engine__eyebrow">Add rule</p><h3 id="add-rule-title">{category}</h3></div>
          <button type="button" onClick={onClose}>Close</button>
        </header>
        <form className="rules-engine__form" onSubmit={onSubmit}>
          <label>Rule name<input required value={draft.name} onChange={(event) => onDraftChange({ ...draft, name: event.target.value })} /></label>
          <label>Status<select value={draft.status} onChange={(event) => onDraftChange({ ...draft, status: event.target.value as RuleStatus })}><option>ACTIVE</option><option>DRAFT</option><option>REVIEW</option><option>DISABLED</option></select></label>
          <div className="rules-engine__condition">
            <label htmlFor={fieldId}>Field</label><select id={fieldId} aria-label="Field" value={draft.field} onChange={(event) => onDraftChange({ ...draft, field: event.target.value })}>{fields.map((field) => <option key={field} value={field}>{field}</option>)}</select>
            <label htmlFor={operatorId}>Operator</label><select id={operatorId} aria-label="Operator" value={draft.operator} onChange={(event) => onDraftChange({ ...draft, operator: event.target.value })}>{operators.map((operator) => <option key={operator} value={operator}>{operator}</option>)}</select>
            <label htmlFor={valueId}>Value</label><input id={valueId} aria-label="Value" required value={draft.value} onChange={(event) => onDraftChange({ ...draft, value: event.target.value })} placeholder="backend-config-ref" />
          </div>
          <RuleBuilderPreview category={category} field={draft.field} operator={draft.operator} value={draft.value || 'value-ref'} />
          <label>Priority<input type="number" min="1" value={draft.priority} onChange={(event) => onDraftChange({ ...draft, priority: Number(event.target.value) })} /></label>
          <label>Notes<textarea value={draft.notes} onChange={(event) => onDraftChange({ ...draft, notes: event.target.value })} placeholder="Lifecycle, validation, UI hint, or governance note" /></label>
          <footer className="rules-engine__drawer-actions"><button type="button" onClick={onClose}>Cancel</button><button type="submit" className="primary">Create draft</button></footer>
        </form>
      </aside>
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return <article className="glass-card rules-engine__metric"><span>{label}</span><strong>{value}</strong></article>;
}

function statusClass(status: RuleStatus) {
  if (status === 'ACTIVE') return 'active';
  if (status === 'REVIEW') return 'review';
  if (status === 'DISABLED') return 'disabled';
  return 'draft';
}

const rulesEngineStyles = `
.rules-engine { color: var(--ds-color-text, #e5eefc); display: grid; gap: 1rem; grid-template-columns: 14rem minmax(0, 1fr); min-height: 100%; padding: 1rem; width: 100%; }
.rules-engine .glass-card { backdrop-filter: blur(24px) saturate(145%); background: linear-gradient(135deg, rgb(255 255 255 / 13%), rgb(255 255 255 / 6%)); border: 1px solid rgb(255 255 255 / 18%); border-radius: 1.25rem; box-shadow: 0 24px 80px rgb(0 0 0 / 24%); }
.rules-engine h2, .rules-engine h3, .rules-engine p { margin: 0; }
.rules-engine h2 { font-size: clamp(1.65rem, 3vw, 2.65rem); }
.rules-engine h3 { font-size: 1rem; }
.rules-engine__eyebrow { color: #67e8f9; font-size: .72rem; font-weight: 900; letter-spacing: .08em; text-transform: uppercase; }
.rules-engine__sidebar { align-self: start; display: grid; gap: .45rem; padding: .75rem; position: sticky; top: 1rem; }
.rules-engine__logo { align-items: center; background: linear-gradient(135deg, #22d3ee, #a78bfa); border-radius: .9rem; color: #03111f; display: inline-flex; font-weight: 950; height: 2.4rem; justify-content: center; width: 2.4rem; }
.rules-engine__sidebar button { align-items: start; display: grid; gap: .15rem; text-align: left; }
.rules-engine__sidebar button[aria-pressed='true'], .rules-engine__builder { background: rgb(255 255 255 / 14%); border-color: rgb(103 232 249 / 42%); }
.rules-engine__sidebar small, .rules-engine small, .rules-engine__bulk span, .rules-engine__metric span { color: rgb(226 232 240 / 70%); font-size: .72rem; }
.rules-engine__main { display: grid; gap: 1rem; min-width: 0; }
.rules-engine__hero, .rules-engine__toolbar, .rules-engine__drawer-head, .rules-engine__drawer-actions { align-items: center; display: flex; gap: 1rem; justify-content: space-between; }
.rules-engine__hero { padding: 1rem; }
.rules-engine__hero p { max-width: 68rem; }
.rules-engine__hero-actions, .rules-engine__filters, .rules-engine__bulk { display: flex; flex-wrap: wrap; gap: .5rem; }
.rules-engine button, .rules-engine input, .rules-engine select, .rules-engine textarea { background: rgb(7 18 34 / 62%); border: 1px solid rgb(255 255 255 / 18%); border-radius: .75rem; color: inherit; font: inherit; padding: .5rem .65rem; }
.rules-engine button { cursor: pointer; font-size: .8rem; font-weight: 900; }
.rules-engine button.primary { background: linear-gradient(135deg, #22d3ee, #818cf8); color: #03111f; }
.rules-engine button:disabled { cursor: not-allowed; opacity: .45; }
.rules-engine textarea { min-height: 4.4rem; resize: vertical; }
.rules-engine__metrics { display: grid; gap: .75rem; grid-template-columns: repeat(4, minmax(0, 1fr)); }
.rules-engine__metric { display: grid; gap: .2rem; padding: .85rem; }
.rules-engine__metric strong { font-size: 1.45rem; }
.rules-engine__workspace { display: grid; gap: .8rem; padding: 1rem; }
.rules-engine__bulk { align-items: center; border-block: 1px solid rgb(255 255 255 / 12%); padding-block: .6rem; }
.rules-engine__builder { align-items: center; border: 1px solid rgb(255 255 255 / 14%); border-radius: 1rem; display: flex; flex-wrap: wrap; gap: .45rem; padding: .65rem; }
.rules-engine__builder span, .rules-engine__builder em, .rules-engine__builder strong { border: 1px solid rgb(255 255 255 / 15%); border-radius: 999px; padding: .32rem .55rem; }
.rules-engine__builder span { color: #bfdbfe; font-size: .72rem; font-weight: 900; text-transform: uppercase; }
.rules-engine__builder em { color: #fde68a; font-style: normal; }
.rules-engine__grid { display: grid; gap: .35rem; overflow-x: auto; }
.rules-engine__row { align-items: center; background: rgb(255 255 255 / 7%); border: 1px solid rgb(255 255 255 / 10%); border-radius: .9rem; display: grid; gap: .5rem; grid-template-columns: 4rem minmax(13rem, 1.15fr) minmax(10rem, 1fr) 9rem minmax(12rem, 1fr) 5.5rem 7rem 10rem; min-width: 76rem; padding: .55rem; }
.rules-engine__row--head { background: rgb(255 255 255 / 12%); color: #bfdbfe; font-size: .7rem; font-weight: 950; letter-spacing: .06em; text-transform: uppercase; }
.rules-engine__row span { min-width: 0; }
.rules-engine__row input, .rules-engine__row select { min-width: 0; width: 100%; }
.rules-engine__status { border: 1px solid currentColor; border-radius: 999px; display: inline-flex; font-size: .7rem; font-weight: 950; margin-bottom: .15rem; padding: .22rem .52rem; }
.rules-engine__status--active { color: #86efac; }
.rules-engine__status--review { color: #fde68a; }
.rules-engine__status--draft { color: #93c5fd; }
.rules-engine__status--disabled { color: #fca5a5; }
.rules-engine__overlay { background: rgb(2 6 23 / 52%); inset: 0; padding: 1rem; position: fixed; z-index: 50; }
.rules-engine__drawer { animation: rules-engine-slide-in .18s ease-out; display: grid; gap: .85rem; margin-left: auto; max-height: calc(100vh - 2rem); max-width: 38rem; overflow: auto; padding: 1rem; width: min(100%, 38rem); }
.rules-engine__form { display: grid; gap: .7rem; }
.rules-engine__form label { display: grid; gap: .25rem; font-size: .78rem; font-weight: 900; }
.rules-engine__condition { display: grid; gap: .55rem; grid-template-columns: 1fr .85fr 1fr; }
@keyframes rules-engine-slide-in { from { opacity: 0; transform: translateX(1rem); } to { opacity: 1; transform: translateX(0); } }
@media (max-width: 1040px) { .rules-engine { grid-template-columns: 1fr; } .rules-engine__sidebar { grid-template-columns: repeat(5, minmax(9rem, 1fr)); overflow-x: auto; position: static; } .rules-engine__hero, .rules-engine__toolbar { align-items: stretch; flex-direction: column; } }
@media (max-width: 720px) { .rules-engine { padding: .5rem; } .rules-engine__metrics, .rules-engine__condition { grid-template-columns: 1fr; } .rules-engine__sidebar { grid-template-columns: 1fr; } .rules-engine__overlay { padding: .5rem; } }
`;

export default RulesEngineScreen;

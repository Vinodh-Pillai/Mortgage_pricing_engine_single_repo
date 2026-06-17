import { useMemo, useState } from 'react';

type AnalysisMode = 'LLM' | 'HEURISTIC';
type Confidence = 'HIGH' | 'MEDIUM' | 'LOW';

type MappingField = {
  sourceField: string;
  canonicalField: string;
  confidence: Confidence;
  required: boolean;
  coercionRule: string;
  status: 'PROPOSED' | 'ACCEPTED' | 'REJECTED' | 'UNMAPPED';
};

const canonicalFields = ['note_rate', 'base_price', 'lock_period', 'canonical_product_key', 'adjustment_type', 'adjustment_value'];

const initialMappings: MappingField[] = [
  { sourceField: 'Rate', canonicalField: 'note_rate', confidence: 'MEDIUM', required: true, coercionRule: 'percent', status: 'PROPOSED' },
  { sourceField: 'Price', canonicalField: 'base_price', confidence: 'MEDIUM', required: true, coercionRule: 'mortgage_price_notation', status: 'PROPOSED' },
  { sourceField: 'Lock Period Days', canonicalField: 'lock_period', confidence: 'MEDIUM', required: true, coercionRule: 'string', status: 'PROPOSED' },
  { sourceField: 'Custom LLPA', canonicalField: '', confidence: 'LOW', required: false, coercionRule: 'bps', status: 'UNMAPPED' },
];

const previewRows = [
  ['Rate', 'Price', 'Lock Period Days', 'Custom LLPA'],
  ['6.500', '99.125', '30', '25 bps'],
  ['6.625', '98.875', '30', '30 bps'],
];

export function MappingWizardScreen() {
  const [mode, setMode] = useState<AnalysisMode>('LLM');
  const [mappings, setMappings] = useState<MappingField[]>(initialMappings);
  const [profileName, setProfileName] = useState('');
  const [investorCode, setInvestorCode] = useState('');
  const [productCode, setProductCode] = useState('');
  const [savedProfile, setSavedProfile] = useState<string>('');

  const normalizedPreview = useMemo(() => {
    const accepted = mappings.filter((mapping) => mapping.status !== 'REJECTED' && mapping.status !== 'UNMAPPED' && mapping.canonicalField);
    return previewRows.slice(1).map((row) => Object.fromEntries(accepted.map((mapping) => {
      const sourceIndex = previewRows[0].indexOf(mapping.sourceField);
      return [mapping.canonicalField, sourceIndex >= 0 ? row[sourceIndex] : ''];
    })));
  }, [mappings]);

  function updateMapping(index: number, patch: Partial<MappingField>) {
    setMappings((current) => current.map((mapping, i) => i === index ? { ...mapping, ...patch } : mapping));
  }

  function saveDraftProfile() {
    if (!profileName || !investorCode || !productCode) {
      setSavedProfile('Profile name, investor code, and product code are required before saving.');
      return;
    }
    setSavedProfile(`Draft profile "${profileName}" saved locally for ${investorCode}/${productCode}; governance remains DRAFT → SIMULATE → APPROVE → PUBLISH.`);
  }

  return (
    <section className="panel" aria-labelledby="mapping-wizard-heading">
      <header className="hero hero--admin">
        <p className="eyebrow">Rate feed onboarding</p>
        <h2 id="mapping-wizard-heading">Rate Sheet Mapping Wizard</h2>
        <p>Upload a sample, review structure-only proposals, edit mappings, preview normalized output, and save a governed draft profile.</p>
      </header>

      <fieldset aria-label="Analysis mode">
        <legend>Analysis mode</legend>
        <button type="button" aria-pressed={mode === 'LLM'} onClick={() => setMode('LLM')}>LLM mode</button>
        <button type="button" aria-pressed={mode === 'HEURISTIC'} onClick={() => setMode('HEURISTIC')}>Heuristic mode</button>
        <p role="status">{mode === 'LLM' ? 'LLM mode uses redacted structure-only prompts and local/mock fallback when no provider is configured.' : 'Heuristic mode maps aliases and matrix/LLPA patterns without external calls.'}</p>
      </fieldset>

      <section className="module-card" aria-labelledby="source-preview-heading">
        <h3 id="source-preview-heading">Source preview</h3>
        <table aria-label="First 10 source rows">
          <tbody>
            {previewRows.map((row, rowIndex) => <tr key={rowIndex}>{row.map((cell) => rowIndex === 0 ? <th key={cell}>{cell}</th> : <td key={cell}>{cell}</td>)}</tr>)}
          </tbody>
        </table>
      </section>

      <section className="module-card" aria-labelledby="mapping-grid-heading">
        <h3 id="mapping-grid-heading">Editable mapping grid</h3>
        <div role="table" aria-label="Proposed field mappings">
          <div role="row">
            <strong role="columnheader">Source</strong><strong role="columnheader">Canonical field</strong><strong role="columnheader">Confidence</strong><strong role="columnheader">Required</strong><strong role="columnheader">Coercion</strong><strong role="columnheader">Decision</strong>
          </div>
          {mappings.map((mapping, index) => (
            <div role="row" key={mapping.sourceField} aria-label={`${mapping.sourceField} mapping`}>
              <span role="cell">{mapping.sourceField}</span>
              <span role="cell"><select aria-label={`Canonical field for ${mapping.sourceField}`} value={mapping.canonicalField} onChange={(event) => updateMapping(index, { canonicalField: event.target.value, status: event.target.value ? 'ACCEPTED' : 'UNMAPPED' })}><option value="">Select canonical field</option>{canonicalFields.map((field) => <option key={field} value={field}>{field}</option>)}</select></span>
              <span role="cell" className={`badge badge--${mapping.confidence.toLowerCase()}`}>{mapping.confidence}</span>
              <span role="cell"><label><input type="checkbox" checked={mapping.required} onChange={(event) => updateMapping(index, { required: event.target.checked })} /> Required</label></span>
              <span role="cell"><select aria-label={`Coercion for ${mapping.sourceField}`} value={mapping.coercionRule} onChange={(event) => updateMapping(index, { coercionRule: event.target.value })}><option>string</option><option>percent</option><option>bps</option><option>mortgage_price_notation</option></select></span>
              <span role="cell"><button type="button" onClick={() => updateMapping(index, { status: mapping.status === 'REJECTED' ? 'ACCEPTED' : 'REJECTED' })}>{mapping.status === 'REJECTED' ? 'Accept' : 'Reject'}</button></span>
            </div>
          ))}
        </div>
        <p>Unmapped fields are highlighted as LOW confidence until an admin selects a canonical target.</p>
      </section>

      <section className="module-card" aria-labelledby="normalized-preview-heading">
        <h3 id="normalized-preview-heading">Real-time normalized output preview</h3>
        <pre>{JSON.stringify(normalizedPreview, null, 2)}</pre>
      </section>

      <section className="module-card" aria-labelledby="save-profile-heading">
        <h3 id="save-profile-heading">Save as governed profile</h3>
        <label>Profile name <input value={profileName} onChange={(event) => setProfileName(event.target.value)} /></label>
        <label>Investor code <input value={investorCode} onChange={(event) => setInvestorCode(event.target.value)} /></label>
        <label>Product code <input value={productCode} onChange={(event) => setProductCode(event.target.value)} /></label>
        <button type="button" onClick={saveDraftProfile}>Save as Profile</button>
        {savedProfile ? <p role="status">{savedProfile}</p> : null}
      </section>
    </section>
  );
}

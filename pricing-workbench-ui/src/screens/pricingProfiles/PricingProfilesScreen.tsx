import { useMemo, useState, type CSSProperties, type ReactNode } from 'react';

type CategoryId = 'profiles' | 'baseRates' | 'margins' | 'calculations' | 'rateFeeds';
type ProfileStatus = 'Draft' | 'Active' | 'Pending' | 'Archived';
type SlideOverKind = 'profile' | 'baseRate' | 'margin' | 'calculation' | 'feed';

type PricingProfile = {
  id: string;
  name: string;
  type: string;
  baseRateSource: string;
  marginPolicy: string;
  status: ProfileStatus;
  owner: string;
};

type BaseRateGrid = {
  id: string;
  investor: string;
  product: string;
  rateTable: string;
  effectiveDates: string;
  status: string;
};

type MarginPolicy = {
  id: string;
  company: string;
  investor: string;
  mlo: string;
  overlays: string;
  profitabilityTargets: string;
  status: string;
};

type CalculationStep = {
  id: string;
  waterfallPreview: string;
  adjustmentComposition: string;
  roundingRules: string;
  source: string;
};

type RateFeedProfile = {
  id: string;
  source: string;
  mapping: string;
  validationRules: string;
  schedule: string;
  status: string;
};

const categories: Array<{ id: CategoryId; label: string; count: string }> = [
  { id: 'profiles', label: 'Pricing Profiles', count: '5' },
  { id: 'baseRates', label: 'Base Rate Grids', count: '4' },
  { id: 'margins', label: 'Margin Policies', count: '4' },
  { id: 'calculations', label: 'Calculations', count: '4' },
  { id: 'rateFeeds', label: 'Rate Feed Profiles', count: '4' },
];

const initialProfiles: PricingProfile[] = [
  { id: 'profile-agency-retail', name: 'Agency Retail', type: 'Retail', baseRateSource: 'rate-feed-service:agency', marginPolicy: 'margin-service:retail', status: 'Active', owner: 'pricing-service' },
  { id: 'profile-agency-broker', name: 'Agency Broker', type: 'Broker', baseRateSource: 'rate-feed-service:agency', marginPolicy: 'margin-service:broker', status: 'Pending', owner: 'pricing-bff' },
  { id: 'profile-jumbo-retail', name: 'Jumbo Retail', type: 'Retail', baseRateSource: 'rate-feed-service:jumbo', marginPolicy: 'margin-service:jumbo', status: 'Draft', owner: 'pricing-service' },
  { id: 'profile-portfolio', name: 'Portfolio', type: 'Portfolio', baseRateSource: 'pricing-service:manual-ref', marginPolicy: 'margin-service:portfolio', status: 'Active', owner: 'pricing-service' },
  { id: 'profile-archived', name: 'Archived Investor', type: 'Investor', baseRateSource: 'rate-feed-service:legacy', marginPolicy: 'margin-service:legacy', status: 'Archived', owner: 'pricing-bff' },
];

const baseRateGrids: BaseRateGrid[] = [
  { id: 'grid-agency-fixed', investor: 'Agency Investor', product: 'Fixed Conventional', rateTable: 'rate-feed-service:grid:agency-fixed', effectiveDates: 'current-version-window', status: 'Active' },
  { id: 'grid-agency-arm', investor: 'Agency Investor', product: 'ARM Conventional', rateTable: 'rate-feed-service:grid:agency-arm', effectiveDates: 'scheduled-version-window', status: 'Pending' },
  { id: 'grid-jumbo', investor: 'Jumbo Investor', product: 'Jumbo', rateTable: 'rate-feed-service:grid:jumbo', effectiveDates: 'draft-version-window', status: 'Draft' },
  { id: 'grid-portfolio', investor: 'Portfolio Desk', product: 'Portfolio', rateTable: 'pricing-service:grid:portfolio', effectiveDates: 'active-version-window', status: 'Active' },
];

const marginPolicies: MarginPolicy[] = [
  { id: 'margin-retail', company: 'Retail Channel', investor: 'Agency Investor', mlo: 'Retail MLO', overlays: 'margin-service:overlay:retail', profitabilityTargets: 'margin-service:target:retail', status: 'Active' },
  { id: 'margin-broker', company: 'Broker Channel', investor: 'Agency Investor', mlo: 'Broker MLO', overlays: 'margin-service:overlay:broker', profitabilityTargets: 'margin-service:target:broker', status: 'Pending' },
  { id: 'margin-jumbo', company: 'Jumbo Channel', investor: 'Jumbo Investor', mlo: 'Jumbo MLO', overlays: 'margin-service:overlay:jumbo', profitabilityTargets: 'margin-service:target:jumbo', status: 'Draft' },
  { id: 'margin-portfolio', company: 'Portfolio Desk', investor: 'Portfolio Desk', mlo: 'Portfolio MLO', overlays: 'margin-service:overlay:portfolio', profitabilityTargets: 'margin-service:target:portfolio', status: 'Active' },
];

const calculationSteps: CalculationStep[] = [
  { id: 'calc-base', waterfallPreview: 'Base rate selection', adjustmentComposition: 'pricing-service:base-rate-ref', roundingRules: 'pricing-service:rounding:base', source: 'pricing-service' },
  { id: 'calc-margin', waterfallPreview: 'Margin application', adjustmentComposition: 'margin-service:policy-ref', roundingRules: 'margin-service:rounding:policy', source: 'margin-service' },
  { id: 'calc-overlay', waterfallPreview: 'Overlay composition', adjustmentComposition: 'margin-service:overlay-ref', roundingRules: 'pricing-service:rounding:overlay', source: 'margin-service' },
  { id: 'calc-final', waterfallPreview: 'Final price', adjustmentComposition: 'pricing-service:calculation-ref', roundingRules: 'pricing-service:rounding:final', source: 'pricing-bff' },
];

const rateFeedProfiles: RateFeedProfile[] = [
  { id: 'feed-agency', source: 'Agency Feed', mapping: 'rate-feed-service:mapping:agency', validationRules: 'rate-feed-service:rules:agency', schedule: 'rate-feed-service:schedule:agency', status: 'Active' },
  { id: 'feed-jumbo', source: 'Jumbo Feed', mapping: 'rate-feed-service:mapping:jumbo', validationRules: 'rate-feed-service:rules:jumbo', schedule: 'rate-feed-service:schedule:jumbo', status: 'Pending' },
  { id: 'feed-portfolio', source: 'Portfolio Feed', mapping: 'rate-feed-service:mapping:portfolio', validationRules: 'rate-feed-service:rules:portfolio', schedule: 'rate-feed-service:schedule:portfolio', status: 'Draft' },
  { id: 'feed-legacy', source: 'Legacy Feed', mapping: 'rate-feed-service:mapping:legacy', validationRules: 'rate-feed-service:rules:legacy', schedule: 'rate-feed-service:schedule:legacy', status: 'Archived' },
];

const versionHistory = [
  { id: 'version-active', version: 'v-current', actor: 'pricing-service', state: 'Active', ref: 'pricing-service:version:current' },
  { id: 'version-pending', version: 'v-next', actor: 'pricing-bff', state: 'Pending', ref: 'pricing-bff:version:next' },
  { id: 'version-draft', version: 'v-draft', actor: 'margin-service', state: 'Draft', ref: 'margin-service:version:draft' },
];

export default function PricingProfilesScreen() {
  const [activeCategory, setActiveCategory] = useState<CategoryId>('profiles');
  const [profiles, setProfiles] = useState(initialProfiles);
  const [selectedProfileIds, setSelectedProfileIds] = useState<string[]>(['profile-agency-retail']);
  const [slideOver, setSlideOver] = useState<SlideOverKind | null>(null);
  const [exportPayload, setExportPayload] = useState('');

  const selectedProfiles = useMemo(() => profiles.filter((profile) => selectedProfileIds.includes(profile.id)), [profiles, selectedProfileIds]);
  const activeProfile = selectedProfiles[0] ?? profiles[0];

  function updateProfile(id: string, patch: Partial<PricingProfile>) {
    setProfiles((current) => current.map((profile) => profile.id === id ? { ...profile, ...patch } : profile));
  }

  function toggleProfile(id: string) {
    setSelectedProfileIds((current) => current.includes(id) ? current.filter((profileId) => profileId !== id) : [...current, id]);
  }

  function bulkActivate() {
    setProfiles((current) => current.map((profile) => selectedProfileIds.includes(profile.id) ? { ...profile, status: 'Active' } : profile));
  }

  function exportState(format: 'json' | 'csv') {
    if (format === 'json') {
      setExportPayload(JSON.stringify({ profiles, baseRateGrids, marginPolicies, calculationSteps, rateFeedProfiles, versionHistory }, null, 2));
      return;
    }
    setExportPayload([
      'id,name,type,baseRateSource,marginPolicy,status,owner',
      ...profiles.map((profile) => [profile.id, profile.name, profile.type, profile.baseRateSource, profile.marginPolicy, profile.status, profile.owner].map(csvCell).join(',')),
    ].join('\n'));
  }

  return (
    <main style={styles.screen} aria-labelledby="pricing-profiles-title">
      <div style={styles.shell}>
        <aside style={styles.sidebar} aria-label="Profile categories">
          <div style={styles.brandBlock}>
            <span style={styles.eyebrow}>Pricing</span>
            <h1 id="pricing-profiles-title" style={styles.title}>Pricing Profiles</h1>
          </div>
          <nav style={styles.navList}>
            {categories.map((category) => (
              <button key={category.id} type="button" style={{ ...styles.navButton, ...(activeCategory === category.id ? styles.navButtonActive : undefined) }} onClick={() => setActiveCategory(category.id)}>
                <span>{category.label}</span>
                <span style={styles.countPill}>{category.count}</span>
              </button>
            ))}
          </nav>
          <div style={styles.sidebarFooter}>
            <button type="button" style={styles.primaryButton} onClick={bulkActivate} disabled={selectedProfileIds.length === 0}>Bulk activate</button>
            <button type="button" style={styles.secondaryButton} onClick={() => setSlideOver('profile')}>New profile</button>
          </div>
        </aside>

        <section style={styles.workspace}>
          <Toolbar selectedCount={selectedProfileIds.length} onBulkActivate={bulkActivate} onImport={() => setSlideOver('profile')} onExportJson={() => exportState('json')} onExportCsv={() => exportState('csv')} />

          <div style={styles.metricsGrid} aria-label="Pricing profile totals">
            <Metric label="Profiles" value={profiles.length} detail="pricing-service" />
            <Metric label="Margins" value={marginPolicies.length} detail="margin-service" />
            <Metric label="Rate feeds" value={rateFeedProfiles.length} detail="rate-feed-service" />
            <Metric label="BFF views" value={calculationSteps.length} detail="pricing-bff" />
          </div>

          <div style={styles.contentGrid}>
            <section style={styles.mainColumn}>
              {(activeCategory === 'profiles' || activeCategory === 'baseRates') && <PricingProfilesSection profiles={profiles} selectedProfileIds={selectedProfileIds} onToggleProfile={toggleProfile} onUpdateProfile={updateProfile} onOpen={() => setSlideOver('profile')} />}
              {(activeCategory === 'baseRates' || activeCategory === 'profiles') && <BaseRateGridsSection rows={baseRateGrids} onOpen={() => setSlideOver('baseRate')} />}
              {(activeCategory === 'margins' || activeCategory === 'profiles') && <MarginPoliciesSection rows={marginPolicies} onOpen={() => setSlideOver('margin')} />}
              {(activeCategory === 'calculations' || activeCategory === 'profiles') && <CalculationsSection rows={calculationSteps} onOpen={() => setSlideOver('calculation')} />}
              {(activeCategory === 'rateFeeds' || activeCategory === 'profiles') && <RateFeedProfilesSection rows={rateFeedProfiles} onOpen={() => setSlideOver('feed')} />}
            </section>

            <aside style={styles.detailColumn} aria-label="Version history">
              <section style={styles.card}>
                <div style={styles.cardHeader}><span style={styles.eyebrow}>Version History</span><StatusPill status={activeProfile.status} /></div>
                <h2 style={styles.cardTitle}>{activeProfile.name}</h2>
                <dl style={styles.definitionGrid}>
                  <dt>Source</dt><dd>{activeProfile.baseRateSource}</dd>
                  <dt>Policy</dt><dd>{activeProfile.marginPolicy}</dd>
                  <dt>Owner</dt><dd>{activeProfile.owner}</dd>
                </dl>
                <div style={styles.timeline}>
                  {versionHistory.map((item) => <div key={item.id} style={styles.timelineItem}><strong>{item.version}</strong><span>{item.actor}</span><StatusPill status={item.state} /><code style={styles.code}>{item.ref}</code></div>)}
                </div>
              </section>

              <section style={styles.card}>
                <div style={styles.cardHeader}><span style={styles.eyebrow}>Import / Export</span></div>
                <div style={styles.actionGrid}>
                  <button type="button" style={styles.secondaryButton} onClick={() => setSlideOver('profile')}>Import</button>
                  <button type="button" style={styles.secondaryButton} onClick={() => exportState('json')}>JSON</button>
                  <button type="button" style={styles.secondaryButton} onClick={() => exportState('csv')}>CSV</button>
                </div>
                {exportPayload ? <textarea style={styles.exportBox} aria-label="Export payload" readOnly value={exportPayload} rows={10} /> : null}
              </section>
            </aside>
          </div>
        </section>
      </div>

      {slideOver ? <SlideOver kind={slideOver} onClose={() => setSlideOver(null)} /> : null}
    </main>
  );
}

function Toolbar({ selectedCount, onBulkActivate, onImport, onExportJson, onExportCsv }: { selectedCount: number; onBulkActivate: () => void; onImport: () => void; onExportJson: () => void; onExportCsv: () => void }) {
  return (
    <div style={styles.toolbar} role="toolbar" aria-label="Pricing profile actions">
      <div style={styles.toolbarTitle}><span style={styles.eyebrow}>Workspace</span><strong>{selectedCount} selected</strong></div>
      <div style={styles.toolbarActions}>
        <button type="button" style={styles.primaryButton} onClick={onBulkActivate} disabled={selectedCount === 0}>Bulk activate</button>
        <button type="button" style={styles.secondaryButton} onClick={onImport}>Import</button>
        <button type="button" style={styles.secondaryButton} onClick={onExportJson}>Export JSON</button>
        <button type="button" style={styles.secondaryButton} onClick={onExportCsv}>Export CSV</button>
      </div>
    </div>
  );
}

function PricingProfilesSection({ profiles, selectedProfileIds, onToggleProfile, onUpdateProfile, onOpen }: { profiles: PricingProfile[]; selectedProfileIds: string[]; onToggleProfile: (id: string) => void; onUpdateProfile: (id: string, patch: Partial<PricingProfile>) => void; onOpen: () => void }) {
  return (
    <GridCard title="Profile Catalog" action="Edit profile" onOpen={onOpen}>
      <table style={styles.table} aria-label="Pricing Profiles">
        <thead><tr><th style={styles.th}>Select</th><th style={styles.th}>Name</th><th style={styles.th}>Type</th><th style={styles.th}>Base rate source</th><th style={styles.th}>Margin policy</th><th style={styles.th}>Status</th></tr></thead>
        <tbody>
          {profiles.map((profile) => (
            <tr key={profile.id}>
              <td style={styles.td}><input type="checkbox" checked={selectedProfileIds.includes(profile.id)} onChange={() => onToggleProfile(profile.id)} aria-label={`Select ${profile.name}`} /></td>
              <td style={styles.td}><InlineInput value={profile.name} onChange={(name) => onUpdateProfile(profile.id, { name })} /></td>
              <td style={styles.td}><InlineInput value={profile.type} onChange={(type) => onUpdateProfile(profile.id, { type })} /></td>
              <td style={styles.td}><InlineInput value={profile.baseRateSource} onChange={(baseRateSource) => onUpdateProfile(profile.id, { baseRateSource })} /></td>
              <td style={styles.td}><InlineInput value={profile.marginPolicy} onChange={(marginPolicy) => onUpdateProfile(profile.id, { marginPolicy })} /></td>
              <td style={styles.td}><select style={styles.inlineControl} value={profile.status} onChange={(event) => onUpdateProfile(profile.id, { status: event.target.value as ProfileStatus })}><option>Draft</option><option>Pending</option><option>Active</option><option>Archived</option></select></td>
            </tr>
          ))}
        </tbody>
      </table>
    </GridCard>
  );
}

function BaseRateGridsSection({ rows, onOpen }: { rows: BaseRateGrid[]; onOpen: () => void }) {
  return <GridCard title="Base Rate Grids" action="Grid form" onOpen={onOpen}><DenseTable columns={['Investor', 'Product', 'Rate table', 'Effective dates', 'Status']} rows={rows.map((row) => [row.investor, row.product, row.rateTable, row.effectiveDates, row.status])} /></GridCard>;
}

function MarginPoliciesSection({ rows, onOpen }: { rows: MarginPolicy[]; onOpen: () => void }) {
  return <GridCard title="Margin Policies" action="Policy form" onOpen={onOpen}><DenseTable columns={['Company', 'Investor', 'MLO', 'Overlays', 'Profitability targets', 'Status']} rows={rows.map((row) => [row.company, row.investor, row.mlo, row.overlays, row.profitabilityTargets, row.status])} /></GridCard>;
}

function CalculationsSection({ rows, onOpen }: { rows: CalculationStep[]; onOpen: () => void }) {
  return <GridCard title="Calculations" action="Calculation form" onOpen={onOpen}><DenseTable columns={['Waterfall preview', 'Adjustment composition', 'Rounding rules', 'Source']} rows={rows.map((row) => [row.waterfallPreview, row.adjustmentComposition, row.roundingRules, row.source])} /></GridCard>;
}

function RateFeedProfilesSection({ rows, onOpen }: { rows: RateFeedProfile[]; onOpen: () => void }) {
  return <GridCard title="Rate Feed Profiles" action="Feed form" onOpen={onOpen}><DenseTable columns={['Source', 'Mapping', 'Validation rules', 'Schedule', 'Status']} rows={rows.map((row) => [row.source, row.mapping, row.validationRules, row.schedule, row.status])} /></GridCard>;
}

function GridCard({ title, action, onOpen, children }: { title: string; action: string; onOpen: () => void; children: ReactNode }) {
  return (
    <section style={styles.card} aria-labelledby={`${slug(title)}-heading`}>
      <div style={styles.cardHeader}>
        <h2 id={`${slug(title)}-heading`} style={styles.cardTitle}>{title}</h2>
        <button type="button" style={styles.secondaryButton} onClick={onOpen}>{action}</button>
      </div>
      <div style={styles.tableScroller}>{children}</div>
    </section>
  );
}

function DenseTable({ columns, rows }: { columns: string[]; rows: string[][] }) {
  return (
    <table style={styles.table}>
      <thead><tr>{columns.map((column) => <th key={column} style={styles.th}>{column}</th>)}</tr></thead>
      <tbody>{rows.map((row, index) => <tr key={`${row[0]}-${index}`}>{row.map((cell, cellIndex) => <td key={`${cell}-${cellIndex}`} style={styles.td}>{cellIndex === row.length - 1 ? <StatusPill status={cell} /> : <span>{cell}</span>}</td>)}</tr>)}</tbody>
    </table>
  );
}

function Metric({ label, value, detail }: { label: string; value: number; detail: string }) {
  return <section style={styles.metric}><span style={styles.eyebrow}>{label}</span><strong style={styles.metricValue}>{value}</strong><code style={styles.code}>{detail}</code></section>;
}

function InlineInput({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return <input style={styles.inlineControl} value={value} onChange={(event) => onChange(event.target.value)} />;
}

function StatusPill({ status }: { status: string }) {
  const tone = status.toLowerCase();
  return <span style={{ ...styles.statusPill, ...(tone.includes('active') ? styles.statusActive : tone.includes('pending') ? styles.statusPending : tone.includes('archived') ? styles.statusArchived : undefined) }}>{status}</span>;
}

function SlideOver({ kind, onClose }: { kind: SlideOverKind; onClose: () => void }) {
  const title = kindLabels[kind];
  const prefix = kind.toLowerCase();
  const nameId = `${prefix}-draft-name`;
  const backendRefId = `${prefix}-backend-ref`;
  const statusId = `${prefix}-draft-status`;
  const versionId = `${prefix}-draft-version`;
  return (
    <div style={styles.overlay} role="presentation">
      <aside style={styles.slideOver} aria-labelledby="slide-over-title">
        <div style={styles.cardHeader}>
          <h2 id="slide-over-title" style={styles.cardTitle}>{title}</h2>
          <button type="button" style={styles.iconButton} onClick={onClose} aria-label="Close">×</button>
        </div>
        <div style={styles.formGrid}>
          <label style={styles.label} htmlFor={nameId}>Name</label><input id={nameId} aria-label="Name" style={styles.formControl} defaultValue={`${title} draft`} />
          <label style={styles.label} htmlFor={backendRefId}>Backend ref</label><input id={backendRefId} aria-label="Backend ref" style={styles.formControl} defaultValue={`${kindLabels[kind].toLowerCase().replaceAll(' ', '-')}:ref`} />
          <label style={styles.label} htmlFor={statusId}>Status</label><select id={statusId} aria-label="Status" style={styles.formControl} defaultValue="Draft"><option>Draft</option><option>Pending</option><option>Active</option><option>Archived</option></select>
          <label style={styles.label} htmlFor={versionId}>Version</label><input id={versionId} aria-label="Version" style={styles.formControl} defaultValue="v-draft" />
        </div>
        <div style={styles.slideActions}>
          <button type="button" style={styles.primaryButton} onClick={onClose}>Save</button>
          <button type="button" style={styles.secondaryButton} onClick={onClose}>Cancel</button>
        </div>
      </aside>
    </div>
  );
}

const kindLabels = {
  profile: 'Pricing Profile',
  baseRate: 'Base Rate Grid',
  margin: 'Margin Policy',
  calculation: 'Calculation',
  feed: 'Rate Feed Profile',
};

function csvCell(value: string) {
  return `"${value.replaceAll('"', '""')}"`;
}

function slug(value: string) {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/(^-|-$)/g, '');
}

const glass = 'linear-gradient(135deg, rgba(255,255,255,0.26), rgba(255,255,255,0.08))';
const border = '1px solid rgba(255,255,255,0.32)';
const shadow = '0 24px 80px rgba(15, 23, 42, 0.16)';

const styles: Record<string, CSSProperties> = {
  screen: {
    width: '100%',
    minHeight: '100vh',
    padding: 'clamp(1rem, 2vw, 2rem)',
    boxSizing: 'border-box',
    color: '#0f172a',
    background: 'radial-gradient(circle at top left, rgba(59,130,246,0.24), transparent 34rem), radial-gradient(circle at bottom right, rgba(14,165,233,0.22), transparent 32rem), #eef6ff',
  },
  shell: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 18rem), 1fr))',
    gap: '1rem',
    width: '100%',
    maxWidth: 'none',
  },
  sidebar: {
    position: 'sticky',
    top: '1rem',
    alignSelf: 'start',
    minHeight: 'calc(100vh - 2rem)',
    padding: '1rem',
    border,
    borderRadius: '1.5rem',
    background: glass,
    boxShadow: shadow,
    backdropFilter: 'blur(24px)',
  },
  brandBlock: { display: 'grid', gap: '0.25rem', marginBottom: '1rem' },
  eyebrow: { color: '#2563eb', fontSize: '0.72rem', fontWeight: 800, letterSpacing: '0.12em', textTransform: 'uppercase' },
  title: { margin: 0, fontSize: 'clamp(1.4rem, 2vw, 2rem)', lineHeight: 1.05 },
  navList: { display: 'grid', gap: '0.55rem' },
  navButton: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '0.75rem', width: '100%', border, borderRadius: '1rem', padding: '0.85rem', background: 'rgba(255,255,255,0.46)', color: '#0f172a', cursor: 'pointer', fontWeight: 800, textAlign: 'left' },
  navButtonActive: { background: 'rgba(37,99,235,0.16)', borderColor: 'rgba(37,99,235,0.42)', boxShadow: 'inset 0 0 0 1px rgba(37,99,235,0.24)' },
  countPill: { borderRadius: '999px', padding: '0.2rem 0.5rem', background: 'rgba(15,23,42,0.08)', fontSize: '0.72rem' },
  sidebarFooter: { display: 'grid', gap: '0.55rem', marginTop: '1rem' },
  workspace: { minWidth: 0, display: 'grid', gap: '1rem' },
  toolbar: { display: 'flex', justifyContent: 'space-between', alignItems: 'center', gap: '1rem', padding: '1rem', border, borderRadius: '1.35rem', background: glass, boxShadow: shadow, backdropFilter: 'blur(22px)', flexWrap: 'wrap' },
  toolbarTitle: { display: 'grid', gap: '0.25rem' },
  toolbarActions: { display: 'flex', flexWrap: 'wrap', gap: '0.5rem' },
  primaryButton: { border: 0, borderRadius: '999px', padding: '0.7rem 1rem', color: '#fff', background: 'linear-gradient(135deg, #2563eb, #0284c7)', boxShadow: '0 14px 30px rgba(37,99,235,0.24)', fontWeight: 800, cursor: 'pointer' },
  secondaryButton: { border, borderRadius: '999px', padding: '0.65rem 0.95rem', color: '#0f172a', background: 'rgba(255,255,255,0.58)', fontWeight: 800, cursor: 'pointer' },
  metricsGrid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 11rem), 1fr))', gap: '0.75rem' },
  metric: { display: 'grid', gap: '0.35rem', padding: '1rem', border, borderRadius: '1.25rem', background: glass, boxShadow: '0 18px 54px rgba(15,23,42,0.1)', backdropFilter: 'blur(20px)', minWidth: 0 },
  metricValue: { fontSize: '2rem', lineHeight: 1 },
  contentGrid: { display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 24rem), 1fr))', gap: '1rem', alignItems: 'start' },
  mainColumn: { display: 'grid', gap: '1rem', minWidth: 0 },
  detailColumn: { display: 'grid', gap: '1rem', minWidth: 0 },
  card: { border, borderRadius: '1.35rem', padding: '1rem', background: glass, boxShadow: shadow, backdropFilter: 'blur(24px)', minWidth: 0 },
  cardHeader: { display: 'flex', justifyContent: 'space-between', gap: '0.75rem', alignItems: 'center', marginBottom: '0.75rem' },
  cardTitle: { margin: 0, fontSize: '1.05rem' },
  tableScroller: { overflowX: 'auto', borderRadius: '1rem', border: '1px solid rgba(148,163,184,0.26)' },
  table: { width: '100%', borderCollapse: 'collapse', minWidth: '58rem', background: 'rgba(255,255,255,0.34)' },
  th: { padding: '0.55rem 0.65rem', textAlign: 'left', fontSize: '0.72rem', textTransform: 'uppercase', letterSpacing: '0.08em', color: '#475569', borderBottom: '1px solid rgba(148,163,184,0.24)', whiteSpace: 'nowrap' },
  td: { padding: '0.45rem 0.65rem', borderBottom: '1px solid rgba(148,163,184,0.18)', whiteSpace: 'nowrap', fontSize: '0.86rem' },
  inlineControl: { width: '100%', minWidth: '9rem', boxSizing: 'border-box', border: '1px solid rgba(148,163,184,0.34)', borderRadius: '0.65rem', padding: '0.4rem 0.5rem', background: 'rgba(255,255,255,0.72)', color: '#0f172a' },
  statusPill: { display: 'inline-flex', alignItems: 'center', borderRadius: '999px', padding: '0.22rem 0.55rem', background: 'rgba(100,116,139,0.12)', color: '#334155', fontSize: '0.75rem', fontWeight: 800 },
  statusActive: { background: 'rgba(34,197,94,0.16)', color: '#166534' },
  statusPending: { background: 'rgba(245,158,11,0.18)', color: '#92400e' },
  statusArchived: { background: 'rgba(100,116,139,0.18)', color: '#475569' },
  definitionGrid: { display: 'grid', gridTemplateColumns: 'auto 1fr', gap: '0.45rem 0.75rem', margin: '0 0 1rem' },
  timeline: { display: 'grid', gap: '0.65rem' },
  timelineItem: { display: 'grid', gap: '0.25rem', padding: '0.75rem', borderRadius: '1rem', border: '1px solid rgba(148,163,184,0.24)', background: 'rgba(255,255,255,0.36)' },
  code: { display: 'inline-block', maxWidth: '100%', overflow: 'hidden', textOverflow: 'ellipsis', color: '#1d4ed8' },
  actionGrid: { display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.5rem' },
  exportBox: { width: '100%', marginTop: '0.75rem', boxSizing: 'border-box', border, borderRadius: '1rem', padding: '0.75rem', background: 'rgba(255,255,255,0.62)', color: '#0f172a' },
  overlay: { position: 'fixed', inset: 0, display: 'flex', justifyContent: 'flex-end', background: 'rgba(15,23,42,0.22)', backdropFilter: 'blur(6px)', zIndex: 30 },
  slideOver: { width: 'min(100%, 28rem)', height: '100%', boxSizing: 'border-box', padding: '1rem', borderLeft: border, background: 'linear-gradient(135deg, rgba(255,255,255,0.9), rgba(239,246,255,0.72))', boxShadow: '-24px 0 80px rgba(15,23,42,0.2)', backdropFilter: 'blur(26px)' },
  iconButton: { width: '2.2rem', height: '2.2rem', borderRadius: '999px', border, background: 'rgba(255,255,255,0.6)', cursor: 'pointer', fontSize: '1.35rem' },
  formGrid: { display: 'grid', gap: '0.85rem' },
  label: { display: 'grid', gap: '0.35rem', fontWeight: 800, fontSize: '0.82rem' },
  formControl: { border: '1px solid rgba(148,163,184,0.4)', borderRadius: '0.85rem', padding: '0.75rem', background: 'rgba(255,255,255,0.72)' },
  slideActions: { display: 'flex', gap: '0.5rem', marginTop: '1rem' },
};

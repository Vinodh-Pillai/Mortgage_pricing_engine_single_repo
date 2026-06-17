import { useMemo, useState } from 'react';
import type { FormEvent } from 'react';

type InvestorStatus = 'ACTIVE' | 'INACTIVE' | 'SETUP';
type DetailTab = 'products' | 'channels' | 'pricingProfiles' | 'contacts' | 'settings';

type Investor = {
  id: string;
  name: string;
  code: string;
  status: InvestorStatus;
  products: string[];
  channels: string[];
  pricingProfiles: string[];
  contacts: Contact[];
  settings: string[];
  notes: string;
};

type Contact = {
  name: string;
  role: string;
  email: string;
  phone: string;
};

type InvestorForm = {
  name: string;
  code: string;
  status: InvestorStatus;
  products: string;
  channels: string;
  pricingProfiles: string;
  contactName: string;
  contactRole: string;
  contactEmail: string;
  contactPhone: string;
  settings: string;
  notes: string;
};

export const investorManagementEvidenceTarget = '.local-harness/evidence/direct-investor-management/investor-management.json';
export const investorManagementStateCoverage = ['ready', 'empty', 'bulk-selection', 'detail-slide-over', 'create-slide-over'];

const emptyForm: InvestorForm = {
  name: '',
  code: '',
  status: 'SETUP',
  products: '',
  channels: '',
  pricingProfiles: '',
  contactName: '',
  contactRole: '',
  contactEmail: '',
  contactPhone: '',
  settings: '',
  notes: '',
};

const initialInvestors: Investor[] = [
  {
    id: 'investor-atlas',
    name: 'Atlas Capital Preview',
    code: 'ATLAS',
    status: 'ACTIVE',
    products: ['Conforming purchase ref', 'Refinance ref', 'Jumbo setup ref'],
    channels: ['Retail', 'Wholesale'],
    pricingProfiles: ['Standard margin profile ref', 'Lock desk profile ref'],
    contacts: [{ name: 'Backend contact ref', role: 'Relationship owner', email: 'catalog-service contact ref', phone: 'tenant-context phone ref' }],
    settings: ['Eligibility source: catalog-service', 'Tenant overrides: tenant-context-service', 'Delivery: setup reference'],
    notes: 'Preview record only; backend APIs own persisted investor policy and configuration values.',
  },
  {
    id: 'investor-harbor',
    name: 'Harbor Funding Setup',
    code: 'HARBOR',
    status: 'SETUP',
    products: ['Government product ref', 'Non-QM product ref'],
    channels: ['Correspondent'],
    pricingProfiles: ['Setup-required profile ref'],
    contacts: [{ name: 'Servicing handoff ref', role: 'Operations', email: 'backend-owned email ref', phone: 'backend-owned phone ref' }],
    settings: ['Activation requires backend contract', 'Bulk assignment allowed as local intent'],
    notes: 'Used to show setup and detail tabs without inventing rates, fees, thresholds, or eligibility rules.',
  },
  {
    id: 'investor-northstar',
    name: 'Northstar Investor Group',
    code: 'NORTH',
    status: 'INACTIVE',
    products: ['Archived product mapping ref'],
    channels: ['Retail', 'Delegated correspondent'],
    pricingProfiles: ['Inactive profile ref'],
    contacts: [{ name: 'Inactive contact ref', role: 'Escalation', email: 'backend-owned escalation ref', phone: 'backend-owned contact ref' }],
    settings: ['Deactivated locally', 'Reactivation requires backend verification'],
    notes: 'Inactive preview record for bulk lifecycle controls.',
  },
];

const tabs: DetailTab[] = ['products', 'channels', 'pricingProfiles', 'contacts', 'settings'];
const sideNav = ['Investors', 'Bulk ops', 'Mappings', 'Contacts', 'Audit'];

function splitValues(value: string) {
  return value.split(',').map((item) => item.trim()).filter(Boolean);
}

function statusClass(status: InvestorStatus) {
  if (status === 'ACTIVE') return 'ready';
  if (status === 'INACTIVE') return 'blocked';
  return 'needs-attention';
}

function formToInvestor(form: InvestorForm, existingCount: number): Investor {
  const code = form.code.trim().toUpperCase();
  return {
    id: `investor-${code.toLowerCase() || existingCount + 1}`,
    name: form.name.trim(),
    code,
    status: form.status,
    products: splitValues(form.products),
    channels: splitValues(form.channels),
    pricingProfiles: splitValues(form.pricingProfiles),
    contacts: [{
      name: form.contactName.trim() || 'Backend contact ref',
      role: form.contactRole.trim() || 'Contact role ref',
      email: form.contactEmail.trim() || 'Backend-owned email ref',
      phone: form.contactPhone.trim() || 'Backend-owned phone ref',
    }],
    settings: splitValues(form.settings),
    notes: form.notes.trim() || 'Local setup draft; backend persistence contract owns final configuration.',
  };
}

export function InvestorManagementScreen() {
  const [investors, setInvestors] = useState<Investor[]>(initialInvestors);
  const [query, setQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | InvestorStatus>('ALL');
  const [selectedIds, setSelectedIds] = useState<string[]>([]);
  const [showCreate, setShowCreate] = useState(false);
  const [activeInvestorId, setActiveInvestorId] = useState<string | null>(initialInvestors[0]?.id ?? null);
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailTab, setDetailTab] = useState<DetailTab>('products');
  const [bulkProduct, setBulkProduct] = useState('Assigned product ref');
  const [formError, setFormError] = useState('');

  const activeInvestor = investors.find((investor) => investor.id === activeInvestorId) ?? null;
  const filteredInvestors = useMemo(() => {
    const search = query.trim().toLowerCase();
    return investors.filter((investor) => {
      const matchesSearch = !search || investor.name.toLowerCase().includes(search) || investor.code.toLowerCase().includes(search) || investor.contacts.some((contact) => contact.name.toLowerCase().includes(search));
      const matchesStatus = statusFilter === 'ALL' || investor.status === statusFilter;
      return matchesSearch && matchesStatus;
    });
  }, [investors, query, statusFilter]);

  const selectedInvestors = investors.filter((investor) => selectedIds.includes(investor.id));
  const stats = useMemo(() => ({
    active: investors.filter((investor) => investor.status === 'ACTIVE').length,
    setup: investors.filter((investor) => investor.status === 'SETUP').length,
    products: investors.reduce((sum, investor) => sum + investor.products.length, 0),
    channels: new Set(investors.flatMap((investor) => investor.channels)).size,
  }), [investors]);

  function toggleSelected(investorId: string) {
    setSelectedIds((current) => current.includes(investorId) ? current.filter((id) => id !== investorId) : [...current, investorId]);
  }

  function setBulkStatus(status: InvestorStatus) {
    setInvestors((current) => current.map((investor) => selectedIds.includes(investor.id) ? { ...investor, status } : investor));
  }

  function assignBulkProduct() {
    const nextProduct = bulkProduct.trim();
    if (!nextProduct) return;
    setInvestors((current) => current.map((investor) => {
      if (!selectedIds.includes(investor.id) || investor.products.includes(nextProduct)) return investor;
      return { ...investor, products: [...investor.products, nextProduct] };
    }));
  }

  function openDetail(investor: Investor, tab: DetailTab = 'products') {
    setActiveInvestorId(investor.id);
    setDetailTab(tab);
    setDetailOpen(true);
  }

  function createInvestor(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const data = new FormData(event.currentTarget);
    const form: InvestorForm = {
      name: String(data.get('name') ?? ''),
      code: String(data.get('code') ?? ''),
      status: String(data.get('status') ?? 'SETUP') as InvestorStatus,
      products: String(data.get('products') ?? ''),
      channels: String(data.get('channels') ?? ''),
      pricingProfiles: String(data.get('pricingProfiles') ?? ''),
      contactName: String(data.get('contactName') ?? ''),
      contactRole: String(data.get('contactRole') ?? ''),
      contactEmail: String(data.get('contactEmail') ?? ''),
      contactPhone: String(data.get('contactPhone') ?? ''),
      settings: String(data.get('settings') ?? ''),
      notes: String(data.get('notes') ?? ''),
    };
    const code = form.code.trim().toUpperCase();
    if (!form.name.trim() || !code) {
      setFormError('Investor name and code are required.');
      return;
    }
    if (investors.some((investor) => investor.code === code)) {
      setFormError('Investor code must be unique in the current grid.');
      return;
    }
    const created = formToInvestor(form, investors.length);
    setInvestors((current) => [created, ...current]);
    setActiveInvestorId(created.id);
    setSelectedIds([created.id]);
    setFormError('');
    setShowCreate(false);
    setDetailOpen(true);
  }

  return (
    <div className="investor-mgmt" aria-labelledby="investor-management-title">
      <style>{investorManagementStyles}</style>
      <aside className="investor-mgmt__rail" aria-label="Investor management sections">
        <span className="investor-mgmt__rail-logo">IM</span>
        {sideNav.map((item) => <a key={item} href={`#${item.toLowerCase().replace(/\s+/g, '-')}`}>{item}</a>)}
      </aside>

      <main className="investor-mgmt__main">
        <section className="investor-mgmt__hero glass-card">
          <div>
            <p className="investor-mgmt__eyebrow">Catalog-service / tenant-context-service setup lane</p>
            <h2 id="investor-management-title">Investor Management</h2>
            <p>Dense admin workspace for investor profiles, product/channel mappings, pricing profile references, contacts, settings, and bulk lifecycle operations.</p>
          </div>
          <div className="investor-mgmt__actions">
            <button type="button" className="primary" onClick={() => setShowCreate(true)}>Add Investor</button>
            <button type="button" onClick={() => selectedIds.length ? setDetailOpen(true) : setShowCreate(true)}>{selectedIds.length ? 'Review selection' : 'Setup first investor'}</button>
          </div>
        </section>

        <section className="investor-mgmt__stats" aria-label="Investor summary">
          <Metric label="Investors" value={investors.length} />
          <Metric label="Active" value={stats.active} />
          <Metric label="Setup" value={stats.setup} />
          <Metric label="Product refs" value={stats.products} />
          <Metric label="Channel refs" value={stats.channels} />
        </section>

        <section id="investors" className="glass-card investor-mgmt__workspace">
          <div className="investor-mgmt__toolbar">
            <div>
              <p className="investor-mgmt__eyebrow">Investors grid</p>
              <h3>Name, code, status, product count, channel count, contact</h3>
            </div>
            <div className="investor-mgmt__filters" role="search" aria-label="Investor filters">
              <input value={query} onChange={(event) => setQuery(event.target.value)} placeholder="Search investors" />
              <select value={statusFilter} onChange={(event) => setStatusFilter(event.target.value as 'ALL' | InvestorStatus)} aria-label="Filter by status">
                <option value="ALL">All</option>
                <option value="ACTIVE">Active</option>
                <option value="SETUP">Setup</option>
                <option value="INACTIVE">Inactive</option>
              </select>
            </div>
          </div>

          <BulkOperations selectedCount={selectedIds.length} bulkProduct={bulkProduct} selectedInvestors={selectedInvestors} onBulkProductChange={setBulkProduct} onActivate={() => setBulkStatus('ACTIVE')} onDeactivate={() => setBulkStatus('INACTIVE')} onAssignProducts={assignBulkProduct} />

          <div className="investor-mgmt__grid" role="table" aria-label="Investor management table">
            <div role="row" className="investor-mgmt__row investor-mgmt__row--head">
              <span role="columnheader">Select</span><span role="columnheader">Name</span><span role="columnheader">Code</span><span role="columnheader">Status</span><span role="columnheader">Products</span><span role="columnheader">Channels</span><span role="columnheader">Contact</span><span role="columnheader">Actions</span>
            </div>
            {filteredInvestors.length === 0 ? <div role="row" className="investor-mgmt__row"><span role="cell">No investors match filters.</span></div> : null}
            {filteredInvestors.map((investor) => {
              const primaryContact = investor.contacts[0];
              return (
                <button type="button" role="row" className="investor-mgmt__row investor-mgmt__row--button" key={investor.id} onClick={() => openDetail(investor)} aria-label={`Open ${investor.name} detail`}>
                  <span role="cell" onClick={(event) => event.stopPropagation()}><input type="checkbox" aria-label={`Select ${investor.code}`} checked={selectedIds.includes(investor.id)} onChange={() => toggleSelected(investor.id)} /></span>
                  <span role="cell"><strong>{investor.name}</strong><small>{investor.notes}</small></span>
                  <span role="cell"><code>{investor.code}</code></span>
                  <span role="cell"><span className={`investor-mgmt__status investor-mgmt__status--${statusClass(investor.status)}`}>{investor.status}</span></span>
                  <span role="cell"><strong>{investor.products.length}</strong></span>
                  <span role="cell"><strong>{investor.channels.length}</strong></span>
                  <span role="cell"><strong>{primaryContact?.name ?? 'Contact ref required'}</strong><small>{primaryContact?.role ?? 'Role ref required'}</small></span>
                  <span role="cell" className="investor-mgmt__row-actions"><em>Details</em></span>
                </button>
              );
            })}
          </div>
        </section>
      </main>

      {showCreate ? <CreateInvestorDrawer error={formError} onClose={() => { setShowCreate(false); setFormError(''); }} onSubmit={createInvestor} /> : null}
      {detailOpen && activeInvestor ? <InvestorDetailDrawer investor={activeInvestor} activeTab={detailTab} onTabChange={setDetailTab} onClose={() => setDetailOpen(false)} /> : null}
    </div>
  );
}

function Metric({ label, value }: { label: string; value: number }) {
  return <article className="glass-card investor-mgmt__metric"><span>{label}</span><strong>{value}</strong></article>;
}

function BulkOperations({ selectedCount, bulkProduct, selectedInvestors, onBulkProductChange, onActivate, onDeactivate, onAssignProducts }: { selectedCount: number; bulkProduct: string; selectedInvestors: Investor[]; onBulkProductChange: (value: string) => void; onActivate: () => void; onDeactivate: () => void; onAssignProducts: () => void }) {
  const disabled = selectedCount === 0;
  return (
    <div id="bulk-ops" className="investor-mgmt__bulk" aria-label="Bulk operations">
      <strong>{selectedCount} selected</strong>
      <button type="button" disabled={disabled} onClick={onActivate}>Activate</button>
      <button type="button" disabled={disabled} onClick={onDeactivate}>Deactivate</button>
      <input value={bulkProduct} onChange={(event) => onBulkProductChange(event.target.value)} aria-label="Product ref to assign" />
      <button type="button" disabled={disabled || !bulkProduct.trim()} onClick={onAssignProducts}>Assign products</button>
      <span>{selectedInvestors.map((investor) => investor.code).join(', ') || 'No selection'}</span>
    </div>
  );
}

function CreateInvestorDrawer({ error, onClose, onSubmit }: { error: string; onClose: () => void; onSubmit: (event: FormEvent<HTMLFormElement>) => void }) {
  return (
    <div className="investor-mgmt__overlay" role="presentation">
      <aside className="investor-mgmt__drawer glass-card" role="dialog" aria-modal="true" aria-labelledby="create-investor-title">
        <header className="investor-mgmt__drawer-head"><div><p className="investor-mgmt__eyebrow">Add Investor</p><h3 id="create-investor-title">Investor details and configuration refs</h3></div><button type="button" onClick={onClose}>Close</button></header>
        {error ? <div className="investor-mgmt__alert" role="alert">{error}</div> : null}
        <form className="investor-mgmt__form" onSubmit={onSubmit}>
          <Field label="Investor name" name="name" required />
          <Field label="Investor code" name="code" required />
          <label>Status<select name="status" defaultValue={emptyForm.status}><option value="SETUP">Setup</option><option value="ACTIVE">Active</option><option value="INACTIVE">Inactive</option></select></label>
          <label>Supported products<textarea name="products" placeholder="Comma-separated product refs" /></label>
          <label>Channels<textarea name="channels" placeholder="Retail, Wholesale, Correspondent" /></label>
          <label>Pricing profiles<textarea name="pricingProfiles" placeholder="Backend-owned pricing profile refs" /></label>
          <Field label="Contact name" name="contactName" />
          <Field label="Contact role" name="contactRole" />
          <Field label="Contact email" name="contactEmail" />
          <Field label="Contact phone" name="contactPhone" />
          <label>Settings<textarea name="settings" placeholder="Comma-separated backend setting refs" /></label>
          <label>Notes<textarea name="notes" placeholder="Operational notes; no pricing constants" /></label>
          <footer className="investor-mgmt__drawer-actions"><button type="button" onClick={onClose}>Cancel</button><button type="submit" className="primary">Create investor</button></footer>
        </form>
      </aside>
    </div>
  );
}

function InvestorDetailDrawer({ investor, activeTab, onTabChange, onClose }: { investor: Investor; activeTab: DetailTab; onTabChange: (tab: DetailTab) => void; onClose: () => void }) {
  return (
    <div className="investor-mgmt__overlay" role="presentation">
      <aside className="investor-mgmt__drawer investor-mgmt__drawer--wide glass-card" role="dialog" aria-modal="true" aria-labelledby="investor-detail-title">
        <header className="investor-mgmt__drawer-head">
          <div><p className="investor-mgmt__eyebrow"><code>{investor.code}</code> · {investor.status}</p><h3 id="investor-detail-title">{investor.name}</h3></div>
          <button type="button" onClick={onClose}>Close</button>
        </header>
        <nav className="investor-mgmt__tabs" aria-label="Investor detail tabs">
          {tabs.map((tab) => <button key={tab} type="button" aria-pressed={activeTab === tab} onClick={() => onTabChange(tab)}>{tabLabel(tab)}</button>)}
        </nav>
        <DetailPanel investor={investor} activeTab={activeTab} />
      </aside>
    </div>
  );
}

function DetailPanel({ investor, activeTab }: { investor: Investor; activeTab: DetailTab }) {
  if (activeTab === 'contacts') {
    return <section className="investor-mgmt__detail-list">{investor.contacts.map((contact) => <article key={`${contact.name}-${contact.role}`}><strong>{contact.name}</strong><span>{contact.role}</span><code>{contact.email}</code><code>{contact.phone}</code></article>)}</section>;
  }
  const values = activeTab === 'products' ? investor.products : activeTab === 'channels' ? investor.channels : activeTab === 'pricingProfiles' ? investor.pricingProfiles : investor.settings;
  return <section className="investor-mgmt__detail-list">{values.map((value) => <article key={value}><strong>{value}</strong><span>{activeTab === 'settings' ? 'Backend configuration reference' : 'Assigned reference'}</span></article>)}</section>;
}

function Field({ label, name, required = false }: { label: string; name: string; required?: boolean }) {
  return <label>{label}<input name={name} required={required} /></label>;
}

function tabLabel(tab: DetailTab) {
  return tab === 'pricingProfiles' ? 'Pricing Profiles' : tab[0].toUpperCase() + tab.slice(1);
}

const investorManagementStyles = `
.investor-mgmt { color: var(--ds-color-text, #e5eefc); display: grid; gap: 1rem; grid-template-columns: 12rem minmax(0, 1fr); min-height: 100%; padding: 1rem; width: 100%; }
.investor-mgmt .glass-card { backdrop-filter: blur(22px); background: linear-gradient(135deg, rgb(255 255 255 / 13%), rgb(255 255 255 / 6%)); border: 1px solid rgb(255 255 255 / 20%); border-radius: 1.25rem; box-shadow: 0 24px 80px rgb(0 0 0 / 24%); }
.investor-mgmt__rail { align-self: start; backdrop-filter: blur(18px); background: rgb(6 18 38 / 58%); border: 1px solid rgb(255 255 255 / 14%); border-radius: 1.25rem; display: grid; gap: .45rem; padding: .75rem; position: sticky; top: 1rem; }
.investor-mgmt__rail-logo { align-items: center; background: linear-gradient(135deg, #7dd3fc, #a78bfa); border-radius: .9rem; color: #051225; display: inline-flex; font-weight: 900; height: 2.4rem; justify-content: center; width: 2.4rem; }
.investor-mgmt__rail a { border-radius: .8rem; color: inherit; font-size: .82rem; font-weight: 800; padding: .65rem .7rem; text-decoration: none; }
.investor-mgmt__rail a:hover, .investor-mgmt__tabs button[aria-pressed='true'] { background: rgb(255 255 255 / 14%); }
.investor-mgmt__main { display: grid; gap: 1rem; min-width: 0; }
.investor-mgmt__hero, .investor-mgmt__toolbar, .investor-mgmt__drawer-head, .investor-mgmt__drawer-actions { align-items: center; display: flex; gap: 1rem; justify-content: space-between; }
.investor-mgmt__hero { padding: 1rem; }
.investor-mgmt h2, .investor-mgmt h3, .investor-mgmt p { margin: 0; }
.investor-mgmt h2 { font-size: clamp(1.65rem, 3vw, 2.6rem); }
.investor-mgmt h3 { font-size: 1rem; }
.investor-mgmt__eyebrow { color: #93c5fd; font-size: .72rem; font-weight: 900; letter-spacing: .08em; text-transform: uppercase; }
.investor-mgmt__actions, .investor-mgmt__filters, .investor-mgmt__bulk, .investor-mgmt__row-actions { display: flex; flex-wrap: wrap; gap: .5rem; }
.investor-mgmt button, .investor-mgmt input, .investor-mgmt select, .investor-mgmt textarea { background: rgb(7 18 34 / 62%); border: 1px solid rgb(255 255 255 / 18%); border-radius: .75rem; color: inherit; font: inherit; padding: .55rem .7rem; }
.investor-mgmt button { cursor: pointer; font-size: .82rem; font-weight: 900; }
.investor-mgmt button.primary { background: linear-gradient(135deg, #38bdf8, #818cf8); color: #03111f; }
.investor-mgmt button:disabled { cursor: not-allowed; opacity: .48; }
.investor-mgmt textarea { min-height: 4.1rem; resize: vertical; }
.investor-mgmt__stats { display: grid; gap: .75rem; grid-template-columns: repeat(5, minmax(0, 1fr)); }
.investor-mgmt__metric { display: grid; gap: .2rem; padding: .85rem; }
.investor-mgmt__metric span, .investor-mgmt small, .investor-mgmt__bulk span { color: rgb(226 232 240 / 72%); font-size: .74rem; }
.investor-mgmt__metric strong { font-size: 1.45rem; }
.investor-mgmt__workspace { display: grid; gap: .85rem; padding: 1rem; }
.investor-mgmt__bulk { align-items: center; border-block: 1px solid rgb(255 255 255 / 12%); padding-block: .65rem; }
.investor-mgmt__grid { display: grid; gap: .35rem; overflow-x: auto; }
.investor-mgmt__row { align-items: center; background: rgb(255 255 255 / 7%); border: 1px solid rgb(255 255 255 / 10%); border-radius: .9rem; color: inherit; display: grid; gap: .55rem; grid-template-columns: 4rem minmax(13rem, 1.25fr) 6rem 7rem 5rem 5rem minmax(11rem, 1fr) 5rem; min-width: 58rem; padding: .65rem; text-align: left; width: 100%; }
.investor-mgmt__row--head { background: rgb(255 255 255 / 12%); color: #bfdbfe; font-size: .72rem; font-weight: 900; letter-spacing: .06em; text-transform: uppercase; }
.investor-mgmt__row--button:hover { border-color: rgb(125 211 252 / 55%); transform: translateY(-1px); }
.investor-mgmt__row span { min-width: 0; }
.investor-mgmt__row small, .investor-mgmt__detail-list span { display: block; margin-top: .2rem; }
.investor-mgmt__status { border: 1px solid currentColor; border-radius: 999px; display: inline-flex; font-size: .72rem; font-weight: 900; padding: .22rem .52rem; }
.investor-mgmt__status--ready { color: #86efac; }
.investor-mgmt__status--blocked { color: #fca5a5; }
.investor-mgmt__status--needs-attention { color: #fde68a; }
.investor-mgmt__overlay { background: rgb(2 6 23 / 50%); inset: 0; padding: 1rem; position: fixed; z-index: 50; }
.investor-mgmt__drawer { animation: investor-slide-in .18s ease-out; display: grid; gap: .9rem; margin-left: auto; max-height: calc(100vh - 2rem); max-width: 33rem; overflow: auto; padding: 1rem; width: min(100%, 33rem); }
.investor-mgmt__drawer--wide { max-width: 42rem; width: min(100%, 42rem); }
.investor-mgmt__form { display: grid; gap: .65rem; grid-template-columns: repeat(2, minmax(0, 1fr)); }
.investor-mgmt__form label { display: grid; gap: .25rem; font-size: .78rem; font-weight: 900; }
.investor-mgmt__form label:has(textarea), .investor-mgmt__drawer-actions { grid-column: 1 / -1; }
.investor-mgmt__tabs { display: flex; flex-wrap: wrap; gap: .45rem; }
.investor-mgmt__detail-list { display: grid; gap: .55rem; }
.investor-mgmt__detail-list article { background: rgb(255 255 255 / 8%); border: 1px solid rgb(255 255 255 / 12%); border-radius: .9rem; display: grid; gap: .2rem; padding: .75rem; }
.investor-mgmt__alert { background: rgb(248 113 113 / 16%); border: 1px solid rgb(248 113 113 / 35%); border-radius: .9rem; padding: .7rem; }
@keyframes investor-slide-in { from { opacity: 0; transform: translateX(1rem); } to { opacity: 1; transform: translateX(0); } }
@media (max-width: 980px) { .investor-mgmt { grid-template-columns: 1fr; } .investor-mgmt__rail { grid-auto-flow: column; overflow-x: auto; position: static; } .investor-mgmt__stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } .investor-mgmt__hero, .investor-mgmt__toolbar { align-items: stretch; flex-direction: column; } }
@media (max-width: 640px) { .investor-mgmt { padding: .5rem; } .investor-mgmt__stats { grid-template-columns: 1fr; } .investor-mgmt__form { grid-template-columns: 1fr; } .investor-mgmt__overlay { padding: .5rem; } }
`;

export default InvestorManagementScreen;

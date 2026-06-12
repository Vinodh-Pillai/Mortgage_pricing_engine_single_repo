import { useState } from 'react';
import type { ProductCatalogArea } from '../../lib/api/products';

type CatalogSectionsProps = {
  areas: ProductCatalogArea[];
};

export function CatalogSections({ areas }: CatalogSectionsProps) {
  const [selectedAreaId, setSelectedAreaId] = useState<string | null>(null);
  const selectedArea = areas.find((area) => area.areaId === selectedAreaId) ?? null;

  if (!areas.length) {
    return <p role="status">No catalog areas configured.</p>;
  }

  return (
    <>
      <div className="module-rail__grid" role="list" aria-label="Product catalog manager sections">
        {areas.map((area) => (
          <article key={area.areaId} className="module-card" role="listitem">
            <p className="module-card__route">{catalogDisplayText(area.status)}</p>
            <strong className="module-card__title">{area.label}</strong>
            <p>{catalogDisplayText(area.guidance)}</p>
            <dl>
              <dt>Area ID</dt>
              <dd><code>{area.areaId}</code></dd>
              <dt>Metadata source</dt>
              <dd>{catalogDisplayText(area.sourceRef)}</dd>
            </dl>
            <ChipList label={`${area.label} fields`} values={area.fields.map(catalogDisplayText)} />
            <ChipList label={`${area.label} validation`} values={area.validationMessages.map(catalogDisplayText)} />
            <button type="button" onClick={() => setSelectedAreaId(area.areaId)}>Open {area.label} details</button>
          </article>
        ))}
      </div>

      {selectedArea ? <CatalogAreaDialog area={selectedArea} onClose={() => setSelectedAreaId(null)} /> : null}
    </>
  );
}

function CatalogAreaDialog({ area, onClose }: { area: ProductCatalogArea; onClose: () => void }) {
  return (
    <div className="panel" role="dialog" aria-modal="true" aria-labelledby="catalog-area-detail-title">
      <div className="panel-heading-row">
        <div>
          <p className="eyebrow">Catalog area detail</p>
          <h3 id="catalog-area-detail-title">{area.label}</h3>
        </div>
        <button type="button" onClick={onClose}>Close details</button>
      </div>
      <dl className="status-grid">
        <dt>Area ID</dt><dd><code>{area.areaId}</code></dd>
        <dt>Status</dt><dd>{catalogDisplayText(area.status)}</dd>
        <dt>Metadata source</dt><dd>{catalogDisplayText(area.sourceRef)}</dd>
        <dt>Guidance</dt><dd>{catalogDisplayText(area.guidance)}</dd>
      </dl>
      <div className="table-like" role="table" aria-label={`${area.label} fields and validation messages`}>
        <div className="table-like__row" role="row">
          <span role="columnheader">Fields</span>
          <span role="columnheader">Validation messages</span>
        </div>
        <div className="table-like__row" role="row">
          <span role="cell"><ChipList label={`${area.label} detail fields`} values={area.fields.map(catalogDisplayText)} /></span>
          <span role="cell"><ChipList label={`${area.label} detail validation`} values={area.validationMessages.map(catalogDisplayText)} /></span>
        </div>
      </div>
      <a href="/admin/governance">Edit Area in governance configuration</a>
    </div>
  );
}

export function ChipList({ label, values }: { label: string; values: string[] }) {
  if (!values.length) return <p className="field-help">No {label.toLowerCase()} provided.</p>;
  return <ul className="chip-list" aria-label={label}>{values.map((value) => <li key={value}>{value}</li>)}</ul>;
}

export function catalogDisplayText(value: string | number | null | undefined) {
  if (value === null || value === undefined || value === '') return 'Not provided';
  return String(value)
    .replace(/backend[- ]owned/gi, 'authoritative')
    .replace(/backend/gi, 'connected service')
    .replace(/blockers?/gi, 'items needing attention')
    .replace(/blocked/gi, 'needs attention')
    .replace(/evidence/gi, 'review record')
    .replace(/audit/gi, 'review')
    .replace(/replay hash|replay/gi, 'review reference')
    .replace(/ui_trace_id|uiTraceId|trace id|trace refs?|correlation id/gi, 'support reference')
    .replace(/_/g, ' ');
}

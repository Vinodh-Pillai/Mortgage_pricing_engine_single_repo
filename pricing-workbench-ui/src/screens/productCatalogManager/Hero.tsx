import { catalogDisplayText } from './CatalogSections';

type HeroProps = {
  tenantContext: string;
  uiTraceId: string;
  fallbackReason: string;
};

export function Hero({ tenantContext, uiTraceId, fallbackReason }: HeroProps) {
  return (
    <section className="hero hero--admin" aria-labelledby="product-catalog-title">
      <p className="eyebrow">Catalog cockpit</p>
      <h2 id="product-catalog-title">Catalog Cockpit</h2>
      <p>
        Review product drafts, domain lists, lifecycle controls, snapshots, events, and review evidence from configured catalog
        metadata. Publishing stays blocked when catalog contracts are unavailable instead of using UI-side product policy.
      </p>
      <dl className="status-grid">
        <dt>Tenant context</dt>
        <dd>{catalogDisplayText(tenantContext)}</dd>
        <dt>Support reference</dt>
        <dd>{catalogDisplayText(fallbackReason)}</dd>
        <dt>UI trace ID</dt>
        <dd>{catalogDisplayText(uiTraceId)}</dd>
      </dl>
    </section>
  );
}

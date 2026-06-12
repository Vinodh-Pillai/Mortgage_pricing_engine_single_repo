import { useEffect, useState } from 'react';
import { fetchProductCatalogManager, type ProductCatalogManagerView } from '../../lib/api/products';
import { DiagnosticsDetails } from '../../components/DiagnosticsDetails';
import { CatalogSections, catalogDisplayText } from './CatalogSections';
import { Hero } from './Hero';
import { LifecycleEvidence } from './LifecycleEvidence';

type ProductCatalogManagerState =
  | { kind: 'loading' }
  | { kind: 'loaded'; view: ProductCatalogManagerView }
  | { kind: 'unreachable'; message: string };

type ProductCatalogManagerScreenProps = {
  tenantContext?: string;
};

export function ProductCatalogManagerScreen({ tenantContext = 'ui-preview-tenant' }: ProductCatalogManagerScreenProps) {
  const [catalogState, setCatalogState] = useState<ProductCatalogManagerState>({ kind: 'loading' });

  useEffect(() => {
    let active = true;
    fetchProductCatalogManager()
      .then((view) => {
        if (active) setCatalogState({ kind: 'loaded', view });
      })
      .catch((error: unknown) => {
        const message = error instanceof Error ? error.message : 'Product catalog manager is unavailable.';
        if (active) setCatalogState({ kind: 'unreachable', message });
      });
    return () => {
      active = false;
    };
  }, []);

  if (catalogState.kind === 'loading') {
    return (
      <section className="panel" aria-labelledby="product-catalog-manager-heading">
        <h2 id="product-catalog-manager-heading">Product Catalog Manager</h2>
        <p role="status">Loading product catalog manager...</p>
      </section>
    );
  }

  if (catalogState.kind === 'unreachable') {
    return (
      <section className="panel" aria-labelledby="product-catalog-manager-heading">
        <h2 id="product-catalog-manager-heading">Product Catalog Manager</h2>
        <div className="banner banner--blocked" role="alert">{catalogState.message}</div>
      </section>
    );
  }

  const view = catalogState.view;
  const actionsDisabled = view.lifecycle.actionsDisabled;

  return (
    <>
      <section className="panel" aria-labelledby="product-catalog-manager-heading" style={{ position: 'sticky', top: 0, zIndex: 1 }}>
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Product catalog</p>
            <h2 id="product-catalog-manager-heading">Product Catalog Manager</h2>
          </div>
          <button type="button" disabled={actionsDisabled} aria-describedby="new-product-supporting-text">
            New Product
          </button>
        </div>
        <p id="new-product-supporting-text" className="field-help">
          Product creation follows the catalog lifecycle state supplied by the connected service; current tenant context is {catalogDisplayText(view.tenantContext || tenantContext)}.
        </p>
      </section>

      <Hero tenantContext={view.tenantContext || tenantContext} uiTraceId={view.uiTraceId} fallbackReason={view.fallbackReason} />

      <section className="panel" aria-labelledby="product-catalog-sections-heading">
        <div className="panel-heading-row">
          <div>
            <p className="eyebrow">Configured catalog areas</p>
            <h2 id="product-catalog-sections-heading">Catalog sections and validation</h2>
          </div>
          <DiagnosticsDetails items={[`Workspace ${view.tenantContext || tenantContext}`, `Support reference: ${view.uiTraceId}`]} />
        </div>
        <p className="field-help">{catalogDisplayText(view.fallbackReason)}</p>
        <CatalogSections areas={view.areas} />
      </section>

      <LifecycleEvidence dependencyStatus={view.dependencyStatus} events={view.events} lifecycle={view.lifecycle} />
    </>
  );
}

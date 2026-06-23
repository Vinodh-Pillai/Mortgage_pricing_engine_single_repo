import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';

export type PromotedPageActions = {
  label: string;
  actions: ReactNode;
};

type PageActionsContextValue = {
  promotedActions: PromotedPageActions | null;
  setPromotedActions: (actions: PromotedPageActions | null) => void;
};

const PageActionsContext = createContext<PageActionsContextValue>({
  promotedActions: null,
  setPromotedActions: () => undefined,
});

export function PageActionsProvider({ children }: { children: ReactNode }) {
  const [promotedActions, setPromotedActions] = useState<PromotedPageActions | null>(null);
  const value = useMemo(() => ({ promotedActions, setPromotedActions }), [promotedActions]);
  return <PageActionsContext.Provider value={value}>{children}</PageActionsContext.Provider>;
}

export function usePageActions() {
  return useContext(PageActionsContext);
}

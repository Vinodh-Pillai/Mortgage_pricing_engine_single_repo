import { createContext, forwardRef, useCallback, useContext, useId, useMemo, useState, type HTMLAttributes, type ReactNode } from 'react';

type LivePriority = 'polite' | 'assertive';

type AriaLiveContextValue = {
  politeMessage: string;
  assertiveMessage: string;
  announce: (message: string, priority?: LivePriority) => void;
};

const AriaLiveContext = createContext<AriaLiveContextValue | null>(null);

export function generateId(prefix = 'wcpe-a11y') {
  return `${prefix}-${Math.random().toString(36).slice(2, 10)}`;
}

export function useStableId(prefix = 'wcpe-a11y', explicitId?: string) {
  const reactId = useId().replace(/:/g, '');
  return explicitId ?? `${prefix}-${reactId}`;
}

export function useAriaLive() {
  const context = useContext(AriaLiveContext);
  if (!context) {
    const announce = () => undefined;
    return { politeMessage: '', assertiveMessage: '', announce };
  }
  return context;
}

export function AriaLiveProvider({ children }: { children: ReactNode }) {
  const [politeMessage, setPoliteMessage] = useState('');
  const [assertiveMessage, setAssertiveMessage] = useState('');

  const announce = useCallback((message: string, priority: LivePriority = 'polite') => {
    if (priority === 'assertive') {
      setAssertiveMessage(message);
    } else {
      setPoliteMessage(message);
    }
  }, []);

  const value = useMemo(() => ({ politeMessage, assertiveMessage, announce }), [announce, assertiveMessage, politeMessage]);

  return (
    <AriaLiveContext.Provider value={value}>
      {children}
      <VisuallyHidden role="status" aria-live="polite" aria-atomic="true" data-testid="aria-live-polite">{politeMessage}</VisuallyHidden>
      <VisuallyHidden role="alert" aria-live="assertive" aria-atomic="true" data-testid="aria-live-assertive">{assertiveMessage}</VisuallyHidden>
    </AriaLiveContext.Provider>
  );
}

export function useAriaDescribedBy(ids: Array<string | undefined | false>) {
  return ids.filter(Boolean).join(' ') || undefined;
}

export function useAriaLabelledBy(ids: Array<string | undefined | false>) {
  return ids.filter(Boolean).join(' ') || undefined;
}

export const VisuallyHidden = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement>>(function VisuallyHidden({ style, ...props }, ref) {
  return (
    <span
      ref={ref}
      style={{
        border: 0,
        clip: 'rect(0 0 0 0)',
        height: 1,
        margin: -1,
        overflow: 'hidden',
        padding: 0,
        position: 'absolute',
        whiteSpace: 'nowrap',
        width: 1,
        ...style,
      }}
      {...props}
    />
  );
});

VisuallyHidden.displayName = 'VisuallyHidden';

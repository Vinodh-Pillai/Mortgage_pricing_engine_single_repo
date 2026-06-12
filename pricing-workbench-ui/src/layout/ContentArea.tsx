import { useEffect, useRef, type ReactNode } from 'react';
import { useLocation } from 'react-router-dom';

export function ContentArea({ children }: { children: ReactNode }) {
  const location = useLocation();
  const mainRef = useRef<HTMLElement>(null);

  useEffect(() => {
    mainRef.current?.focus({ preventScroll: true });
    if (typeof mainRef.current?.scrollTo === 'function') {
      mainRef.current.scrollTo({ top: 0, behavior: 'smooth' });
    }
  }, [location.pathname]);

  return (
    <main id="main-content" ref={mainRef} className="layout-content" tabIndex={-1}>
      <div className="layout-content__panel">{children}</div>
    </main>
  );
}

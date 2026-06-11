import type { ReactNode } from 'react';

export function ContentArea({ children }: { children: ReactNode }) {
  return <main id="main-content" className="layout-content" tabIndex={-1}>{children}</main>;
}

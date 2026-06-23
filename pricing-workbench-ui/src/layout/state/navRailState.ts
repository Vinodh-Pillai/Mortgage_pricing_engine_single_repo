export const navRailStorageKey = 'loanweft:layout-shell:nav-rail-collapsed';

export function readPersistedNavRailCollapsed() {
  if (typeof window === 'undefined') return true;
  const persisted = window.localStorage.getItem(navRailStorageKey);
  return persisted === null ? true : persisted === 'true';
}

export function persistNavRailCollapsed(collapsed: boolean) {
  window.localStorage.setItem(navRailStorageKey, String(collapsed));
}

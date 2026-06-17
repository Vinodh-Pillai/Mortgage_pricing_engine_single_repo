export const navRailStorageKey = 'loanweft:layout-shell:nav-rail-collapsed';

export function readPersistedNavRailCollapsed() {
  if (typeof window === 'undefined') return false;
  return window.localStorage.getItem(navRailStorageKey) === 'true';
}

export function persistNavRailCollapsed(collapsed: boolean) {
  window.localStorage.setItem(navRailStorageKey, String(collapsed));
}

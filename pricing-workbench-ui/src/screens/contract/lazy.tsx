import { lazy, Suspense, type ComponentType, type ReactNode } from 'react';
import type { ScreenProps } from './ScreenProps';
import { ScreenSkeleton } from './VisualState';

export type LazyScreenImport = () => Promise<{ default: ComponentType<ScreenProps> }>;

export function lazyScreen(importFn: LazyScreenImport) {
  let preloadPromise: Promise<{ default: ComponentType<ScreenProps> }> | undefined;
  const preload = () => {
    preloadPromise ??= importFn();
    return preloadPromise;
  };

  const Component = lazy(preload);
  return { Component, preload };
}

export function LazyScreenBoundary({ children, fallback }: { children: ReactNode; fallback?: ReactNode }) {
  return <Suspense fallback={fallback ?? <ScreenSkeleton />}>{children}</Suspense>;
}

export function preloadOnIntent(preload?: () => Promise<unknown>) {
  return {
    onMouseEnter: () => { void preload?.(); },
    onFocus: () => { void preload?.(); },
  };
}

import { Children, cloneElement, isValidElement, useEffect, useMemo, useState, type KeyboardEvent as ReactKeyboardEvent, type ReactElement, type ReactNode } from 'react';

export type KeyBinding = {
  key: string;
  handler: (event: KeyboardEvent | ReactKeyboardEvent) => void;
  preventDefault?: boolean;
};

export function useEscapeKey(handler: (event: KeyboardEvent) => void, enabled = true) {
  useEffect(() => {
    if (!enabled) return undefined;
    const listener = (event: KeyboardEvent) => {
      if (event.key === 'Escape') handler(event);
    };
    document.addEventListener('keydown', listener);
    return () => document.removeEventListener('keydown', listener);
  }, [enabled, handler]);
}

export function useKeyboardNavigation(bindings: KeyBinding[]) {
  const bindingMap = useMemo(() => new Map(bindings.map((binding) => [binding.key, binding])), [bindings]);
  return (event: ReactKeyboardEvent) => {
    const binding = bindingMap.get(event.key);
    if (!binding) return;
    if (binding.preventDefault ?? true) event.preventDefault();
    binding.handler(event);
  };
}

export function useTabOrder(count: number, activeIndex = 0) {
  return Array.from({ length: count }, (_, index) => (index === activeIndex ? 0 : -1));
}

export function moveRovingIndex(currentIndex: number, key: string, itemCount: number, wrap = true) {
  if (itemCount <= 0) return 0;
  const lastIndex = itemCount - 1;
  if (key === 'Home') return 0;
  if (key === 'End') return lastIndex;
  if (key === 'ArrowRight' || key === 'ArrowDown') return currentIndex === lastIndex ? (wrap ? 0 : lastIndex) : currentIndex + 1;
  if (key === 'ArrowLeft' || key === 'ArrowUp') return currentIndex === 0 ? (wrap ? lastIndex : 0) : currentIndex - 1;
  return currentIndex;
}

type RovingChildProps = {
  onKeyDown?: (event: ReactKeyboardEvent<Element>) => void;
  tabIndex?: number;
};

export function KeyHandler({ bindings, children }: { bindings: KeyBinding[]; children: ReactElement<RovingChildProps> }) {
  const onKeyDown = useKeyboardNavigation(bindings);
  return cloneElement(children, { onKeyDown });
}

export function RovingTabIndex({ children, initialIndex = 0, wrap = true }: { children: ReactNode; initialIndex?: number; wrap?: boolean }) {
  const items = Children.toArray(children).filter(isValidElement) as ReactElement<RovingChildProps>[];
  const [activeIndex, setActiveIndex] = useState(initialIndex);
  const tabOrder = useTabOrder(items.length, activeIndex);

  return (
    <>
      {items.map((child, index) => cloneElement(child, {
        key: child.key ?? index,
        tabIndex: tabOrder[index],
        onKeyDown: (event: ReactKeyboardEvent) => {
          child.props.onKeyDown?.(event);
          const nextIndex = moveRovingIndex(index, event.key, items.length, wrap);
          if (nextIndex !== index) {
            event.preventDefault();
            setActiveIndex(nextIndex);
            const target = event.currentTarget.parentElement?.querySelectorAll<HTMLElement>('[tabindex]')[nextIndex];
            target?.focus();
          }
        },
      }))}
    </>
  );
}

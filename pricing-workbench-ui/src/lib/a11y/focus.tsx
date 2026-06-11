import { forwardRef, useEffect, useRef, type HTMLAttributes, type RefObject } from 'react';

const focusableSelector = [
  'a[href]',
  'area[href]',
  'button:not([disabled])',
  'input:not([disabled])',
  'select:not([disabled])',
  'textarea:not([disabled])',
  'iframe',
  'object',
  'embed',
  '[contenteditable="true"]',
  '[tabindex]:not([tabindex="-1"])',
].join(',');

export function getFocusableElements(container: HTMLElement | null) {
  if (!container) return [];
  return Array.from(container.querySelectorAll<HTMLElement>(focusableSelector)).filter((element) => element.getAttribute('aria-hidden') !== 'true' && element.tabIndex !== -1);
}

export function useFocusRestore() {
  const previousFocusRef = useRef<HTMLElement | null>(null);

  useEffect(() => {
    previousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
    return () => {
      previousFocusRef.current?.focus?.();
    };
  }, []);

  return previousFocusRef;
}

export function useFocusOnMount<T extends HTMLElement>(ref: RefObject<T | null>, enabled = true) {
  useEffect(() => {
    if (enabled) ref.current?.focus();
  }, [enabled, ref]);
}

export function useFocusVisiblePolyfill(root: Document | HTMLElement = document) {
  useEffect(() => {
    const target = root instanceof Document ? root.documentElement : root;
    target.classList.add('focus-visible-ready');
    return () => target.classList.remove('focus-visible-ready');
  }, [root]);
}

export const FocusVisiblePolyfill = () => {
  useFocusVisiblePolyfill();
  return null;
};

export const FocusTrap = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { active?: boolean; restoreFocus?: boolean; initialFocusRef?: RefObject<HTMLElement | null> }>(
  function FocusTrap({ active = true, restoreFocus = true, initialFocusRef, onKeyDown, children, ...props }, ref) {
    const containerRef = useRef<HTMLDivElement | null>(null);
    const previousFocusRef = useRef<HTMLElement | null>(null);

    useEffect(() => {
      if (!active) return undefined;
      previousFocusRef.current = document.activeElement instanceof HTMLElement ? document.activeElement : null;
      const target = initialFocusRef?.current ?? getFocusableElements(containerRef.current)[0] ?? containerRef.current;
      target?.focus();

      return () => {
        if (restoreFocus) previousFocusRef.current?.focus?.();
      };
    }, [active, initialFocusRef, restoreFocus]);

    return (
      <div
        ref={(node) => {
          containerRef.current = node;
          if (typeof ref === 'function') ref(node);
          else if (ref) ref.current = node;
        }}
        tabIndex={-1}
        onKeyDown={(event) => {
          onKeyDown?.(event);
          if (!active || event.key !== 'Tab') return;
          const focusable = getFocusableElements(containerRef.current);
          if (focusable.length === 0) {
            event.preventDefault();
            containerRef.current?.focus();
            return;
          }
          const first = focusable[0];
          const last = focusable[focusable.length - 1];
          if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
          } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
          }
        }}
        {...props}
      >
        {children}
      </div>
    );
  },
);

FocusTrap.displayName = 'FocusTrap';

export const SkipLink = forwardRef<HTMLAnchorElement, HTMLAttributes<HTMLAnchorElement> & { href?: string }>(function SkipLink({ href = '#main-content', children = 'Skip to main content', ...props }, ref) {
  return <a ref={ref} className="skip-link" href={href} {...props}>{children}</a>;
});

SkipLink.displayName = 'SkipLink';

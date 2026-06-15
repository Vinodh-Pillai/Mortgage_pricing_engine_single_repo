import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { VisuallyHidden } from './aria';

type AnnouncePriority = 'polite' | 'assertive';

type AnnouncerContextValue = {
  announce: (message: string, priority?: AnnouncePriority) => void;
};

const AnnouncerContext = createContext<AnnouncerContextValue | null>(null);

export function Announcer({ children }: { children?: ReactNode }) {
  const [politeMessage, setPoliteMessage] = useState('');
  const [assertiveMessage, setAssertiveMessage] = useState('');
  const announce = useCallback((message: string, priority: AnnouncePriority = 'polite') => {
    if (priority === 'assertive') setAssertiveMessage(message);
    else setPoliteMessage(message);
  }, []);
  const value = useMemo(() => ({ announce }), [announce]);

  useEffect(() => {
    const listener = (event: Event) => {
      const detail = (event as CustomEvent<{ message: string; priority?: AnnouncePriority }>).detail;
      if (detail?.message) announce(detail.message, detail.priority);
    };
    window.addEventListener('wcpe:a11y-announce', listener);
    return () => window.removeEventListener('wcpe:a11y-announce', listener);
  }, [announce]);

  return (
    <AnnouncerContext.Provider value={value}>
      {children}
      <VisuallyHidden role="status" aria-live="polite" aria-atomic="true" data-testid="announcer-polite">{politeMessage}</VisuallyHidden>
      <VisuallyHidden role="alert" aria-live="assertive" aria-atomic="true" data-testid="announcer-assertive">{assertiveMessage}</VisuallyHidden>
    </AnnouncerContext.Provider>
  );
}

export function useAnnounce() {
  const context = useContext(AnnouncerContext);
  return useCallback((message: string, priority: AnnouncePriority = 'polite') => {
    if (context) context.announce(message, priority);
    else window.dispatchEvent(new CustomEvent('wcpe:a11y-announce', { detail: { message, priority } }));
  }, [context]);
}

export function usePageTitle(title: string, appName = 'LoanWeft') {
  useEffect(() => {
    const previousTitle = document.title;
    document.title = title.includes(appName) ? title : `${title} | ${appName}`;
    return () => {
      document.title = previousTitle;
    };
  }, [appName, title]);
}

export function validateHeadingHierarchy(levels: number[]) {
  const violations: string[] = [];
  let previous = 0;
  for (const level of levels) {
    if (level < 1 || level > 6) violations.push(`Invalid heading level ${level}`);
    if (previous > 0 && level > previous + 1) violations.push(`Heading level skipped from h${previous} to h${level}`);
    previous = level;
  }
  return { valid: violations.length === 0, violations };
}

export function getHeadingLevels(root: ParentNode = document) {
  return Array.from(root.querySelectorAll('h1,h2,h3,h4,h5,h6')).map((heading) => Number(heading.tagName.slice(1)));
}

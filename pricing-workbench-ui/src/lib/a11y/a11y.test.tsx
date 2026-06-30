import '@testing-library/jest-dom/vitest';
import { act, fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { colors } from '../../design-system/tokens';
import { AriaLiveProvider, FocusTrap, RovingTabIndex, Announcer, axeDependencyStatus, contrastRatio, formatAccessibleNumber, meetsContrastRatio, setDocumentLanguage, useAnnounce, useAriaDescribedBy, useAriaLabelledBy, useEscapeKey, usePageTitle, useReducedMotion, validateContrastPair, validateHeadingHierarchy } from '.';

function AriaHarness() {
  const describedBy = useAriaDescribedBy(['help', false, 'error']);
  const labelledBy = useAriaLabelledBy(['label', undefined]);
  return <input aria-describedby={describedBy} aria-labelledby={labelledBy} />;
}

function AnnounceButton() {
  const announce = useAnnounce();
  return <button onClick={() => announce('Route changed')}>Announce</button>;
}

function EscapeHarness({ onEscape }: { onEscape: () => void }) {
  useEscapeKey(onEscape);
  return <div>Escape ready</div>;
}

function PageTitleHarness({ title }: { title: string }) {
  usePageTitle(title);
  return <div />;
}

function ReducedMotionHarness() {
  const reduced = useReducedMotion();
  return <div>{reduced ? 'reduced' : 'motion-ok'}</div>;
}

describe('PII-24-S06 accessibility foundation', () => {
  it('traps tab focus within container and restores focus on unmount', () => {
    render(<button>Before</button>);
    const before = screen.getByRole('button', { name: 'Before' });
    before.focus();

    const { unmount } = render(
      <FocusTrap aria-label="Modal focus region">
        <button>First</button>
        <button>Last</button>
      </FocusTrap>,
    );

    const first = screen.getByRole('button', { name: 'First' });
    const last = screen.getByRole('button', { name: 'Last' });
    expect(first).toHaveFocus();
    last.focus();
    fireEvent.keyDown(last, { key: 'Tab' });
    expect(first).toHaveFocus();
    fireEvent.keyDown(first, { key: 'Tab', shiftKey: true });
    expect(last).toHaveFocus();
    unmount();
    expect(before).toHaveFocus();
  });

  it('builds ARIA relationships and announces live messages', () => {
    render(
      <AriaLiveProvider>
        <AriaHarness />
      </AriaLiveProvider>,
    );

    expect(screen.getByRole('textbox')).toHaveAttribute('aria-describedby', 'help error');
    expect(screen.getByRole('textbox')).toHaveAttribute('aria-labelledby', 'label');
    expect(screen.getByTestId('aria-live-polite')).toBeInTheDocument();
  });

  it('supports global screen reader announcements and page titles', () => {
    const previousTitle = document.title;
    const { unmount } = render(
      <Announcer>
        <AnnounceButton />
        <PageTitleHarness title="Workbench overview" />
      </Announcer>,
    );

    fireEvent.click(screen.getByRole('button', { name: 'Announce' }));
    expect(screen.getByTestId('announcer-polite')).toHaveTextContent('Route changed');
    expect(document.title).toBe('Workbench overview | LoanWeft');
    unmount();
    document.title = previousTitle;
  });

  it('handles Escape and roving tabindex keyboard navigation', () => {
    const onEscape = vi.fn();
    render(
      <>
        <EscapeHarness onEscape={onEscape} />
        <div role="tablist">
          <RovingTabIndex>
            <button role="tab">One</button>
            <button role="tab">Two</button>
          </RovingTabIndex>
        </div>
      </>,
    );

    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onEscape).toHaveBeenCalledTimes(1);
    const first = screen.getByRole('tab', { name: 'One' });
    const second = screen.getByRole('tab', { name: 'Two' });
    expect(first).toHaveAttribute('tabindex', '0');
    fireEvent.keyDown(first, { key: 'ArrowRight' });
    expect(second).toHaveAttribute('tabindex', '0');
  });

  it('calculates contrast and validates WCAG AA token pairs', () => {
    expect(contrastRatio('#000000', '#ffffff')).toBe(21);
    expect(meetsContrastRatio(colors.dark.text, colors.dark.background, 'text')).toBe(true);
    expect(validateContrastPair(colors.light.borderStrong, colors.light.background, 'ui')).toEqual(expect.objectContaining({ passes: true, requiredRatio: 3 }));
  });

  it('validates heading hierarchy and locale helpers', () => {
    expect(validateHeadingHierarchy([1, 2, 3])).toEqual({ valid: true, violations: [] });
    expect(validateHeadingHierarchy([1, 3]).valid).toBe(false);
    setDocumentLanguage('en-US');
    expect(document.documentElement.lang).toBe('en-US');
    expect(formatAccessibleNumber(1234.5, 'en-US')).toBe('1,234.5');
  });

  it('reports reduced motion and unavailable axe dependencies explicitly', () => {
    Object.defineProperty(window, 'matchMedia', {
      writable: true,
      value: vi.fn().mockImplementation(() => ({
        matches: true,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      })),
    });

    act(() => render(<ReducedMotionHarness />));
    expect(screen.getByText('reduced')).toBeInTheDocument();
    expect(axeDependencyStatus()).toBe('dependency_unavailable');
  });
});

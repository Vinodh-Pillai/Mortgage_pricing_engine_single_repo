import { forwardRef, type ComponentPropsWithoutRef, type ElementType, type HTMLAttributes, type ReactNode } from 'react';
import { useTranslation } from '../../lib/i18n';
import { cx, variantClass } from './variants';

type BoxProps<T extends ElementType = 'div'> = HTMLAttributes<HTMLElement> & { as?: T };

export const Box = forwardRef<HTMLElement, BoxProps>(function Box({ as: Component = 'div', className, ...props }, ref) {
  const AsComponent = Component as ElementType;
  return <AsComponent ref={ref} className={cx('ds-box', className)} {...props} />;
});
Box.displayName = 'Box';

export const Flex = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function Flex({ className, ...props }, ref) {
  return <div ref={ref} className={cx('ds-flex', className)} {...props} />;
});
Flex.displayName = 'Flex';

export const Grid = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function Grid({ className, ...props }, ref) {
  return <div ref={ref} className={cx('ds-grid', className)} {...props} />;
});
Grid.displayName = 'Grid';

export const Text = forwardRef<HTMLParagraphElement, HTMLAttributes<HTMLParagraphElement>>(function Text({ className, ...props }, ref) {
  return <p ref={ref} className={cx('ds-text', className)} {...props} />;
});
Text.displayName = 'Text';

export const Heading = forwardRef<HTMLHeadingElement, HTMLAttributes<HTMLHeadingElement> & { level?: 1 | 2 | 3 | 4 | 5 | 6 }>(
  function Heading({ level = 2, className, ...props }, ref) {
    const Component = `h${level}` as ElementType;
    return <Component ref={ref} className={cx('ds-heading', className)} {...props} />;
  },
);
Heading.displayName = 'Heading';

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger';
type Size = 'sm' | 'md' | 'lg';
type State = 'default' | 'hover' | 'focus' | 'disabled' | 'loading' | 'error';

const sizeClasses: Record<Size, string> = { sm: 'ds-size-sm', md: 'ds-size-md', lg: 'ds-size-lg' };
const variantClasses: Record<Variant, string> = { primary: 'ds-variant-primary', secondary: 'ds-variant-secondary', ghost: 'ds-variant-ghost', danger: 'ds-variant-danger' };
const stateClasses: Record<State, string> = { default: '', hover: 'ds-state-hover', focus: 'ds-state-focus', disabled: 'ds-state-disabled', loading: 'ds-state-loading', error: 'ds-state-error' };

export type ButtonProps = ComponentPropsWithoutRef<'button'> & { variant?: Variant; size?: Size; state?: State; loading?: boolean };

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button({ className, variant = 'primary', size = 'md', state = 'default', loading, disabled, children, ...props }, ref) {
  const computedState = loading ? 'loading' : disabled ? 'disabled' : state;
  const { t } = useTranslation('common');
  return (
    <button
      ref={ref}
      className={cx(variantClass('ds-control ds-button', { variant: variantClasses[variant], size: sizeClasses[size], state: stateClasses[computedState] }), className)}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      {...props}
    >
      {loading ? <Spinner size="sm" aria-label={t('loading')} /> : null}
      {children}
    </button>
  );
});
Button.displayName = 'Button';

type ControlProps<T extends HTMLElement> = HTMLAttributes<T> & { size?: Size; state?: State };

type InputProps = Omit<ComponentPropsWithoutRef<'input'>, 'size'> & { size?: Size; state?: State };

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input({ className, size = 'md', state = 'default', ...props }, ref) {
  return <input ref={ref} className={cx('ds-control ds-input', sizeClasses[size], stateClasses[state], className)} aria-invalid={state === 'error' || undefined} {...props} />;
});
Input.displayName = 'Input';

type SelectProps = Omit<ComponentPropsWithoutRef<'select'>, 'size'> & { size?: Size; state?: State };

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select({ className, size = 'md', state = 'default', ...props }, ref) {
  return <select ref={ref} className={cx('ds-control ds-select', sizeClasses[size], stateClasses[state], className)} aria-invalid={state === 'error' || undefined} {...props} />;
});
Select.displayName = 'Select';

export const Textarea = forwardRef<HTMLTextAreaElement, ComponentPropsWithoutRef<'textarea'> & { state?: State }>(function Textarea({ className, state = 'default', ...props }, ref) {
  return <textarea ref={ref} className={cx('ds-control ds-textarea', stateClasses[state], className)} aria-invalid={state === 'error' || undefined} {...props} />;
});
Textarea.displayName = 'Textarea';

export const Checkbox = forwardRef<HTMLInputElement, ComponentPropsWithoutRef<'input'>>(function Checkbox({ className, type: _type, ...props }, ref) {
  return <input ref={ref} type="checkbox" className={cx('ds-check', className)} {...props} />;
});
Checkbox.displayName = 'Checkbox';

export const Radio = forwardRef<HTMLInputElement, ComponentPropsWithoutRef<'input'>>(function Radio({ className, type: _type, ...props }, ref) {
  return <input ref={ref} type="radio" className={cx('ds-radio', className)} {...props} />;
});
Radio.displayName = 'Radio';

export const Switch = forwardRef<HTMLButtonElement, ButtonProps & { checked?: boolean }>(function Switch({ className, checked = false, ...props }, ref) {
  return <button ref={ref} role="switch" aria-checked={checked} className={cx('ds-switch', checked && 'ds-switch--checked', className)} {...props} />;
});
Switch.displayName = 'Switch';

export const Label = forwardRef<HTMLLabelElement, ComponentPropsWithoutRef<'label'>>(function Label({ className, ...props }, ref) {
  return <label ref={ref} className={cx('ds-label', className)} {...props} />;
});
Label.displayName = 'Label';

export const Card = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function Card({ className, ...props }, ref) {
  return <div ref={ref} className={cx('ds-card', className)} {...props} />;
});
Card.displayName = 'Card';

export const Table = forwardRef<HTMLTableElement, ComponentPropsWithoutRef<'table'>>(function Table({ className, ...props }, ref) {
  return <table ref={ref} className={cx('ds-table', className)} {...props} />;
});
Table.displayName = 'Table';

export const Tabs = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function Tabs({ className, ...props }, ref) {
  return <div ref={ref} className={cx('ds-tabs', className)} {...props} />;
});
Tabs.displayName = 'Tabs';

export const TabList = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function TabList({ className, ...props }, ref) {
  return <div ref={ref} role="tablist" className={cx('ds-tab-list', className)} {...props} />;
});
TabList.displayName = 'TabList';

export const TabPanel = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function TabPanel({ className, ...props }, ref) {
  return <div ref={ref} role="tabpanel" className={cx('ds-tab-panel', className)} {...props} />;
});
TabPanel.displayName = 'TabPanel';

export const Accordion = forwardRef<HTMLDetailsElement, ComponentPropsWithoutRef<'details'>>(function Accordion({ className, ...props }, ref) {
  return <details ref={ref} className={cx('ds-accordion', className)} {...props} />;
});
Accordion.displayName = 'Accordion';

export const Modal = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { open?: boolean; title?: string }>(function Modal({ className, open = true, title, children, ...props }, ref) {
  if (!open) return null;
  return (
    <div ref={ref} role="dialog" aria-modal="true" aria-label={title} className={cx('ds-modal', className)} {...props}>
      <div className="ds-modal__panel">{children}</div>
    </div>
  );
});
Modal.displayName = 'Modal';

export const Drawer = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { open?: boolean }>(function Drawer({ className, open = true, ...props }, ref) {
  return open ? <div ref={ref} role="dialog" className={cx('ds-drawer', className)} {...props} /> : null;
});
Drawer.displayName = 'Drawer';

export const Tooltip = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement>>(function Tooltip({ className, ...props }, ref) {
  return <span ref={ref} role="tooltip" className={cx('ds-tooltip', className)} {...props} />;
});
Tooltip.displayName = 'Tooltip';

export const Popover = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function Popover({ className, ...props }, ref) {
  return <div ref={ref} role="dialog" className={cx('ds-popover', className)} {...props} />;
});
Popover.displayName = 'Popover';

export const Dropdown = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function Dropdown({ className, ...props }, ref) {
  return <div ref={ref} role="menu" className={cx('ds-dropdown', className)} {...props} />;
});
Dropdown.displayName = 'Dropdown';

export const Avatar = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { initials?: string }>(function Avatar({ className, initials, children, ...props }, ref) {
  return <div ref={ref} className={cx('ds-avatar', className)} {...props}>{children ?? initials}</div>;
});
Avatar.displayName = 'Avatar';

export const Badge = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement> & { variant?: Variant }>(function Badge({ className, variant = 'secondary', ...props }, ref) {
  return <span ref={ref} className={cx('ds-badge', variantClasses[variant], className)} {...props} />;
});
Badge.displayName = 'Badge';

export const Chip = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement>>(function Chip({ className, ...props }, ref) {
  return <span ref={ref} className={cx('ds-chip', className)} {...props} />;
});
Chip.displayName = 'Chip';

export const Icon = forwardRef<SVGSVGElement, ComponentPropsWithoutRef<'svg'> & { size?: 16 | 20 | 24 }>(function Icon({ className, size = 20, children, ...props }, ref) {
  return <svg ref={ref} width={size} height={size} viewBox="0 0 24 24" fill="none" aria-hidden="true" className={cx('ds-icon', className)} {...props}>{children ?? <path d="M4 12h16M12 4v16" stroke="currentColor" strokeWidth="2" strokeLinecap="round" />}</svg>;
});
Icon.displayName = 'Icon';

export const Spinner = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement> & { size?: Size }>(function Spinner({ className, size = 'md', ...props }, ref) {
  return <span ref={ref} role="status" className={cx('ds-spinner', sizeClasses[size], className)} {...props} />;
});
Spinner.displayName = 'Spinner';

export const Skeleton = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function Skeleton({ className, ...props }, ref) {
  return <div ref={ref} aria-hidden="true" className={cx('ds-skeleton', className)} {...props} />;
});
Skeleton.displayName = 'Skeleton';

export const Divider = forwardRef<HTMLHRElement, ComponentPropsWithoutRef<'hr'>>(function Divider({ className, ...props }, ref) {
  return <hr ref={ref} className={cx('ds-divider', className)} {...props} />;
});
Divider.displayName = 'Divider';

export const VisuallyHidden = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement>>(function VisuallyHidden({ className, ...props }, ref) {
  return <span ref={ref} className={cx('ds-visually-hidden', className)} {...props} />;
});
VisuallyHidden.displayName = 'VisuallyHidden';

export type { ControlProps, ReactNode };

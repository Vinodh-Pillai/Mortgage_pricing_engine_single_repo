import { forwardRef, useMemo, useState, type ComponentPropsWithoutRef, type CSSProperties, type ElementType, type HTMLAttributes, type ReactNode } from 'react';
import { useTranslation } from '../../lib/i18n';
import { roleColors, type RoleColor, type RoleColorKey } from '../tokens';
import { cx, cva, variantClass } from './variants';

type Size = 'sm' | 'md' | 'lg';
type State = 'default' | 'hover' | 'focus' | 'disabled' | 'loading' | 'error';
type Variant = 'primary' | 'secondary' | 'ghost' | 'danger' | 'glass';
type SurfaceVariant = 'default' | 'glass';

type BoxProps<T extends ElementType = 'div'> = HTMLAttributes<HTMLElement> & { as?: T; variant?: SurfaceVariant };
type ControlProps<T extends HTMLElement> = HTMLAttributes<T> & { size?: Size; state?: State; variant?: SurfaceVariant };

const sizeClasses: Record<Size, string> = { sm: 'ds-size-sm', md: 'ds-size-md', lg: 'ds-size-lg' };
const variantClasses: Record<Variant, string> = { primary: 'ds-variant-primary', secondary: 'ds-variant-secondary', ghost: 'ds-variant-ghost', danger: 'ds-variant-danger', glass: 'ds-variant-glass' };
const stateClasses: Record<State, string> = { default: '', hover: 'ds-state-hover', focus: 'ds-state-focus', disabled: 'ds-state-disabled', loading: 'ds-state-loading', error: 'ds-state-error' };
const surfaceClasses: Record<SurfaceVariant, string> = { default: '', glass: 'ds-surface-glass' };

const controlClass = cva('ds-control', {
  variants: { variant: variantClasses, size: sizeClasses, state: stateClasses },
  defaultVariants: { variant: 'primary', size: 'md', state: 'default' },
});

const touchTargetStyle = { minHeight: '44px', minWidth: '44px' } as const;

export const Box = forwardRef<HTMLElement, BoxProps>(function Box({ as: Component = 'div', className, variant = 'default', ...props }, ref) {
  const AsComponent = Component as ElementType;
  return <AsComponent ref={ref} className={cx('ds-box', surfaceClasses[variant], className)} {...props} />;
});
Box.displayName = 'Box';

export const Flex = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { variant?: SurfaceVariant }>(function Flex({ className, variant = 'default', ...props }, ref) {
  return <div ref={ref} className={cx('ds-flex', surfaceClasses[variant], className)} {...props} />;
});
Flex.displayName = 'Flex';

export const Grid = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { variant?: SurfaceVariant }>(function Grid({ className, variant = 'default', ...props }, ref) {
  return <div ref={ref} className={cx('ds-grid', surfaceClasses[variant], className)} {...props} />;
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

export type ButtonProps = ComponentPropsWithoutRef<'button'> & { variant?: Variant; size?: Size; state?: State; loading?: boolean };

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button({ className, variant = 'primary', size = 'md', state = 'default', loading, disabled, children, style, ...props }, ref) {
  const computedState = loading ? 'loading' : disabled ? 'disabled' : state;
  const { t } = useTranslation('common');
  return (
    <button ref={ref} className={cx(controlClass({ variant, size, state: computedState }), 'ds-button', className)} disabled={disabled || loading} aria-busy={loading || undefined} style={{ ...touchTargetStyle, ...style }} {...props}>
      {loading ? <Spinner size="sm" aria-label={t('loading')} /> : null}
      {children}
    </button>
  );
});
Button.displayName = 'Button';

type InputProps = Omit<ComponentPropsWithoutRef<'input'>, 'size'> & { size?: Size; state?: State; variant?: SurfaceVariant; label?: ReactNode };

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input({ className, size = 'md', state = 'default', variant = 'default', label, style, ...props }, ref) {
  const input = <input ref={ref} className={cx('ds-control ds-input', sizeClasses[size], stateClasses[state], variant === 'glass' && 'ds-input--glass', className)} aria-invalid={state === 'error' || undefined} style={{ ...touchTargetStyle, ...style }} {...props} />;
  return label ? <label className="ds-floating-field"><span>{label}</span>{input}</label> : input;
});
Input.displayName = 'Input';

type SelectProps = Omit<ComponentPropsWithoutRef<'select'>, 'size'> & { size?: Size; state?: State; variant?: SurfaceVariant };
const SelectBase = forwardRef<HTMLSelectElement, SelectProps>(function SelectBase({ className, size = 'md', state = 'default', variant = 'default', style, ...props }, ref) {
  return <select ref={ref} className={cx('ds-control ds-select', sizeClasses[size], stateClasses[state], variant === 'glass' && 'ds-select--glass', className)} aria-invalid={state === 'error' || undefined} style={{ ...touchTargetStyle, ...style }} {...props} />;
});
SelectBase.displayName = 'Select';

const SelectOption = forwardRef<HTMLOptionElement, ComponentPropsWithoutRef<'option'>>(function SelectOption({ className, ...props }, ref) {
  return <option ref={ref} className={cx('ds-select__option', className)} {...props} />;
});
SelectOption.displayName = 'Select.Option';

const SelectGroup = forwardRef<HTMLOptGroupElement, ComponentPropsWithoutRef<'optgroup'>>(function SelectGroup({ className, ...props }, ref) {
  return <optgroup ref={ref} className={cx('ds-select__group', className)} {...props} />;
});
SelectGroup.displayName = 'Select.Group';

export const Select = Object.assign(SelectBase, { Option: SelectOption, Group: SelectGroup });

export const Textarea = forwardRef<HTMLTextAreaElement, ComponentPropsWithoutRef<'textarea'> & { state?: State; variant?: SurfaceVariant }>(function Textarea({ className, state = 'default', variant = 'default', style, ...props }, ref) {
  return <textarea ref={ref} className={cx('ds-control ds-textarea', stateClasses[state], variant === 'glass' && 'ds-textarea--glass', className)} aria-invalid={state === 'error' || undefined} style={{ minHeight: '44px', ...style }} {...props} />;
});
Textarea.displayName = 'Textarea';

export const Checkbox = forwardRef<HTMLInputElement, ComponentPropsWithoutRef<'input'> & { variant?: SurfaceVariant }>(function Checkbox({ className, type: _type, variant = 'default', ...props }, ref) {
  return <input ref={ref} type="checkbox" className={cx('ds-check', variant === 'glass' && 'ds-check--glass', className)} {...props} />;
});
Checkbox.displayName = 'Checkbox';

export const Radio = forwardRef<HTMLInputElement, ComponentPropsWithoutRef<'input'> & { variant?: SurfaceVariant }>(function Radio({ className, type: _type, variant = 'default', ...props }, ref) {
  return <input ref={ref} type="radio" className={cx('ds-radio', variant === 'glass' && 'ds-radio--glass', className)} {...props} />;
});
Radio.displayName = 'Radio';

export const Switch = forwardRef<HTMLButtonElement, ButtonProps & { checked?: boolean; variant?: SurfaceVariant }>(function Switch({ className, checked = false, variant = 'default', style, ...props }, ref) {
  return <button ref={ref} role="switch" aria-checked={checked} className={cx('ds-switch', checked && 'ds-switch--checked', variant === 'glass' && 'ds-switch--glass', className)} style={{ ...touchTargetStyle, ...style }} {...props} />;
});
Switch.displayName = 'Switch';

export const Label = forwardRef<HTMLLabelElement, ComponentPropsWithoutRef<'label'>>(function Label({ className, ...props }, ref) {
  return <label ref={ref} className={cx('ds-label', className)} {...props} />;
});
Label.displayName = 'Label';

export const Card = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { variant?: SurfaceVariant }>(function Card({ className, variant = 'default', ...props }, ref) {
  return <div ref={ref} className={cx('ds-card', variant === 'glass' && 'ds-card--glass', className)} {...props} />;
});
Card.displayName = 'Card';

export const Table = forwardRef<HTMLTableElement, ComponentPropsWithoutRef<'table'> & { variant?: SurfaceVariant }>(function Table({ className, variant = 'default', ...props }, ref) {
  return <table ref={ref} className={cx('ds-table', variant === 'glass' && 'ds-table--glass', className)} {...props} />;
});
Table.displayName = 'Table';

const TabsBase = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { variant?: SurfaceVariant }>(function TabsBase({ className, variant = 'default', ...props }, ref) {
  return <div ref={ref} className={cx('ds-tabs', variant === 'glass' && 'ds-tabs--glass', className)} {...props} />;
});
TabsBase.displayName = 'Tabs';

export const TabList = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { variant?: SurfaceVariant }>(function TabList({ className, variant = 'default', ...props }, ref) {
  return <div ref={ref} role="tablist" className={cx('ds-tab-list', variant === 'glass' && 'ds-tab-list--glass', className)} {...props} />;
});
TabList.displayName = 'TabList';

const TabTrigger = forwardRef<HTMLButtonElement, ComponentPropsWithoutRef<'button'> & { active?: boolean }>(function TabTrigger({ className, active, type = 'button', ...props }, ref) {
  return <button ref={ref} type={type} role="tab" aria-selected={active} className={cx('ds-tab-trigger', active && 'ds-tab-trigger--active', className)} {...props} />;
});
TabTrigger.displayName = 'Tabs.Trigger';

export const TabPanel = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { variant?: SurfaceVariant }>(function TabPanel({ className, variant = 'default', ...props }, ref) {
  return <div ref={ref} role="tabpanel" className={cx('ds-tab-panel', variant === 'glass' && 'ds-tab-panel--glass', className)} {...props} />;
});
TabPanel.displayName = 'TabPanel';

export const Tabs = Object.assign(TabsBase, { List: TabList, Trigger: TabTrigger, Panel: TabPanel });

export const Accordion = forwardRef<HTMLDetailsElement, ComponentPropsWithoutRef<'details'> & { variant?: SurfaceVariant }>(function Accordion({ className, variant = 'default', ...props }, ref) {
  return <details ref={ref} className={cx('ds-accordion', variant === 'glass' && 'ds-accordion--glass', className)} {...props} />;
});
Accordion.displayName = 'Accordion';

const ModalHeader = forwardRef<HTMLElement, HTMLAttributes<HTMLElement>>(function ModalHeader({ className, ...props }, ref) { return <header ref={ref} className={cx('ds-modal__header', className)} {...props} />; });
const ModalBody = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function ModalBody({ className, ...props }, ref) { return <div ref={ref} className={cx('ds-modal__body', className)} {...props} />; });
const ModalFooter = forwardRef<HTMLElement, HTMLAttributes<HTMLElement>>(function ModalFooter({ className, ...props }, ref) { return <footer ref={ref} className={cx('ds-modal__footer', className)} {...props} />; });

const ModalBase = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { open?: boolean; title?: string; variant?: SurfaceVariant }>(function ModalBase({ className, open = true, title, variant = 'default', children, ...props }, ref) {
  if (!open) return null;
  return <div ref={ref} role="dialog" aria-modal="true" aria-label={title} className={cx('ds-modal', variant === 'glass' && 'ds-modal--glass', className)} {...props}><div className="ds-modal__panel">{children}</div></div>;
});
ModalBase.displayName = 'Modal';
export const Modal = Object.assign(ModalBase, { Header: ModalHeader, Body: ModalBody, Footer: ModalFooter });

const DrawerHeader = forwardRef<HTMLElement, HTMLAttributes<HTMLElement>>(function DrawerHeader({ className, ...props }, ref) { return <header ref={ref} className={cx('ds-drawer__header', className)} {...props} />; });
const DrawerBody = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement>>(function DrawerBody({ className, ...props }, ref) { return <div ref={ref} className={cx('ds-drawer__body', className)} {...props} />; });
const DrawerFooter = forwardRef<HTMLElement, HTMLAttributes<HTMLElement>>(function DrawerFooter({ className, ...props }, ref) { return <footer ref={ref} className={cx('ds-drawer__footer', className)} {...props} />; });

const DrawerBase = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { open?: boolean; variant?: SurfaceVariant }>(function DrawerBase({ className, open = true, variant = 'default', ...props }, ref) {
  return open ? <div ref={ref} role="dialog" className={cx('ds-drawer', variant === 'glass' && 'ds-drawer--glass', className)} {...props} /> : null;
});
DrawerBase.displayName = 'Drawer';
export const Drawer = Object.assign(DrawerBase, { Header: DrawerHeader, Body: DrawerBody, Footer: DrawerFooter });

export const Tooltip = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement> & { variant?: SurfaceVariant }>(function Tooltip({ className, variant = 'default', ...props }, ref) {
  return <span ref={ref} role="tooltip" className={cx('ds-tooltip', variant === 'glass' && 'ds-tooltip--glass', className)} {...props} />;
});
Tooltip.displayName = 'Tooltip';

export const Popover = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { variant?: SurfaceVariant }>(function Popover({ className, variant = 'default', ...props }, ref) {
  return <div ref={ref} role="dialog" className={cx('ds-popover', variant === 'glass' && 'ds-popover--glass', className)} {...props} />;
});
Popover.displayName = 'Popover';

export const Dropdown = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { variant?: SurfaceVariant }>(function Dropdown({ className, variant = 'default', ...props }, ref) {
  return <div ref={ref} role="menu" className={cx('ds-dropdown', variant === 'glass' && 'ds-dropdown--glass', className)} {...props} />;
});
Dropdown.displayName = 'Dropdown';

export const Avatar = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { initials?: string; roleColor?: string | RoleColor; roleLabel?: string; size?: Size; variant?: SurfaceVariant }>(function Avatar({ className, initials, children, roleColor, roleLabel, size = 'md', variant = 'default', style, ...props }, ref) {
  const accent = typeof roleColor === 'string' ? roleColor : roleColor?.bg;
  return <div ref={ref} className={cx('ds-avatar', `ds-avatar--${size}`, variant === 'glass' && 'ds-avatar--glass', roleColor && 'ds-avatar--role-badged', className)} style={{ ...style, '--ds-avatar-role-color': accent } as CSSProperties} {...props}>{children ?? initials}{roleColor ? <span className="ds-avatar__role-badge" aria-label={roleLabel ? `${roleLabel} role` : 'Persona role'} /> : null}</div>;
});
Avatar.displayName = 'Avatar';

export const Badge = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement> & { variant?: Variant; dot?: boolean }>(function Badge({ className, variant = 'secondary', dot, children, ...props }, ref) {
  return <span ref={ref} className={cx('ds-badge', variantClasses[variant], dot && 'ds-badge--dot', className)} {...props}>{dot ? <span className="ds-badge__dot" aria-hidden="true" /> : null}{children}</span>;
});
Badge.displayName = 'Badge';

export const Chip = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement> & { variant?: SurfaceVariant; removable?: boolean; onRemove?: () => void }>(function Chip({ className, variant = 'default', removable, onRemove, children, ...props }, ref) {
  return <span ref={ref} className={cx('ds-chip', variant === 'glass' && 'ds-chip--glass', className)} {...props}>{children}{removable ? <button type="button" className="ds-chip__remove" onClick={onRemove} aria-label="Remove chip">×</button> : null}</span>;
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

export const Progress = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { value?: number; max?: number; variant?: SurfaceVariant }>(function Progress({ className, value = 0, max = 100, variant = 'default', ...props }, ref) {
  const percent = Math.max(0, Math.min(100, (value / max) * 100));
  return <div ref={ref} role="progressbar" aria-valuemin={0} aria-valuemax={max} aria-valuenow={value} className={cx('ds-progress', variant === 'glass' && 'ds-progress--glass', className)} {...props}><span className="ds-progress__bar" style={{ inlineSize: `${percent}%` }} /></div>;
});
Progress.displayName = 'Progress';

export const Skeleton = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { variant?: SurfaceVariant }>(function Skeleton({ className, variant = 'default', ...props }, ref) {
  return <div ref={ref} aria-hidden="true" className={cx('ds-skeleton', variant === 'glass' && 'ds-skeleton--glass', className)} {...props} />;
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

const StepperStep = forwardRef<HTMLLIElement, HTMLAttributes<HTMLLIElement> & { status?: 'complete' | 'current' | 'upcoming' | 'blocked'; index?: number }>(function StepperStep({ className, status = 'upcoming', index, children, ...props }, ref) {
  return <li ref={ref} className={cx('ds-stepper__step', `ds-stepper__step--${status}`, className)} aria-current={status === 'current' ? 'step' : undefined} {...props}><span className="ds-stepper__marker">{index}</span><span className="ds-stepper__label">{children}</span></li>;
});
const StepperConnector = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement>>(function StepperConnector({ className, ...props }, ref) { return <span ref={ref} className={cx('ds-stepper__connector', className)} aria-hidden="true" {...props} />; });
const StepperBase = forwardRef<HTMLOListElement, ComponentPropsWithoutRef<'ol'> & { variant?: SurfaceVariant }>(function StepperBase({ className, variant = 'default', ...props }, ref) {
  return <ol ref={ref} className={cx('ds-stepper', variant === 'glass' && 'ds-stepper--glass', className)} {...props} />;
});
export const Stepper = Object.assign(StepperBase, { Step: StepperStep, Connector: StepperConnector });

export const FieldGroup = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { label: ReactNode; help?: ReactNode; error?: ReactNode; required?: boolean }>(function FieldGroup({ className, label, help, error, required, children, ...props }, ref) {
  return <div ref={ref} className={cx('ds-field-group', Boolean(error) && 'ds-field-group--error', className)} {...props}><Label>{label}{required ? <span aria-hidden="true"> *</span> : null}</Label>{children}{help ? <small className="ds-field-group__help">{help}</small> : null}{error ? <small className="ds-field-group__error">{error}</small> : null}</div>;
});

export const Section = forwardRef<HTMLElement, HTMLAttributes<HTMLElement> & { title: ReactNode; collapsible?: boolean; defaultOpen?: boolean; variant?: SurfaceVariant }>(function Section({ className, title, collapsible, defaultOpen = true, variant = 'default', children, ...props }, ref) {
  const [open, setOpen] = useState(defaultOpen);
  return <section ref={ref} className={cx('ds-section', variant === 'glass' && 'ds-section--glass', className)} {...props}><header className="ds-section__header"><Heading level={3}>{title}</Heading>{collapsible ? <Button type="button" variant="ghost" size="sm" onClick={() => setOpen((value) => !value)} aria-expanded={open}>{open ? 'Collapse' : 'Expand'}</Button> : null}</header>{open ? <div className="ds-section__body">{children}</div> : null}</section>;
});

type DataTableColumn = { key: string; header: ReactNode; render?: (row: Record<string, ReactNode>) => ReactNode; sortable?: boolean };
export const DataTable = forwardRef<HTMLTableElement, { columns: DataTableColumn[]; rows: Record<string, ReactNode>[]; caption?: ReactNode; variant?: SurfaceVariant; emptyMessage?: ReactNode } & Omit<ComponentPropsWithoutRef<'table'>, 'children'>>(function DataTable({ columns, rows, caption, variant = 'default', emptyMessage = 'No records', className, ...props }, ref) {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const sortedRows = useMemo(() => sortKey ? [...rows].sort((a, b) => String(a[sortKey] ?? '').localeCompare(String(b[sortKey] ?? ''))) : rows, [rows, sortKey]);
  return <table ref={ref} className={cx('ds-table ds-data-table', variant === 'glass' && 'ds-table--glass', className)} {...props}>{caption ? <caption>{caption}</caption> : null}<thead><tr>{columns.map((column) => <th key={column.key}>{column.sortable ? <button type="button" className="ds-data-table__sort" onClick={() => setSortKey(column.key)}>{column.header}</button> : column.header}</th>)}</tr></thead><tbody>{sortedRows.length ? sortedRows.map((row, rowIndex) => <tr key={rowIndex}>{columns.map((column) => <td key={column.key}>{column.render ? column.render(row) : row[column.key]}</td>)}</tr>) : <tr><td colSpan={columns.length}>{emptyMessage}</td></tr>}</tbody></table>;
});

export const EmptyState = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { title: ReactNode; message?: ReactNode; action?: ReactNode; illustration?: ReactNode; variant?: SurfaceVariant }>(function EmptyState({ className, title, message, action, illustration, variant = 'default', ...props }, ref) {
  return <div ref={ref} className={cx('ds-state-panel ds-empty-state', variant === 'glass' && 'ds-state-panel--glass', className)} {...props}>{illustration ? <div className="ds-state-panel__icon">{illustration}</div> : null}<Heading level={3}>{title}</Heading>{message ? <Text>{message}</Text> : null}{action}</div>;
});

export const BlockedState = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { title?: ReactNode; message: ReactNode; actions?: ReactNode; variant?: SurfaceVariant }>(function BlockedState({ title = 'Blocked', message, actions, variant = 'glass', className, ...props }, ref) {
  return <div ref={ref} className={cx('ds-state-panel ds-blocked-state', variant === 'glass' && 'ds-state-panel--glass', className)} role="status" {...props}><Heading level={3}>{title}</Heading><Text>{message}</Text>{actions}</div>;
});

export const NeedsAttentionState = forwardRef<HTMLDivElement, HTMLAttributes<HTMLDivElement> & { title?: ReactNode; guidance: ReactNode; links?: ReactNode; variant?: SurfaceVariant }>(function NeedsAttentionState({ title = 'Needs attention', guidance, links, variant = 'glass', className, ...props }, ref) {
  return <div ref={ref} className={cx('ds-state-panel ds-needs-attention-state', variant === 'glass' && 'ds-state-panel--glass', className)} role="status" {...props}><Heading level={3}>{title}</Heading><Text>{guidance}</Text>{links}</div>;
});

export const RoleBadge = forwardRef<HTMLSpanElement, HTMLAttributes<HTMLSpanElement> & { role: RoleColorKey; icon?: ReactNode; label?: ReactNode }>(function RoleBadge({ role, icon, label, className, style, ...props }, ref) {
  const color = roleColors[role];
  return <span ref={ref} className={cx('ds-role-badge', className)} style={{ ...style, '--ds-role-badge-bg': color.bg, '--ds-role-badge-text': color.text, '--ds-role-badge-border': color.border } as CSSProperties} {...props}>{icon ? <span className="ds-role-badge__icon">{icon}</span> : null}{label ?? role.replaceAll('-', ' ')}</span>;
});

export type { ControlProps, ReactNode, RoleColorKey };

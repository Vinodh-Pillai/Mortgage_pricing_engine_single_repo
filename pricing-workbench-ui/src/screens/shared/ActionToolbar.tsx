export interface ToolbarAction {
  id: string;
  label: string;
  variant?: 'primary' | 'secondary' | 'ghost' | 'danger';
  disabled?: boolean;
}

export interface ActionToolbarProps {
  label: string;
  primaryActions?: ToolbarAction[];
  secondaryActions?: ToolbarAction[];
  onAction?: (actionId: string) => void;
}

export function ActionToolbar({ label, primaryActions = [], secondaryActions = [], onAction }: ActionToolbarProps) {
  const renderAction = (action: ToolbarAction) => (
    <button
      key={action.id}
      type="button"
      className={`ds-control ds-button ds-size-sm ds-variant-${action.variant ?? 'secondary'}`}
      disabled={action.disabled}
      onClick={() => onAction?.(action.id)}
    >
      {action.label}
    </button>
  );

  return (
    <div className="action-toolbar" aria-label={label}>
      <div className="action-toolbar__group">{primaryActions.map(renderAction)}</div>
      <div className="action-toolbar__group">{secondaryActions.map(renderAction)}</div>
    </div>
  );
}

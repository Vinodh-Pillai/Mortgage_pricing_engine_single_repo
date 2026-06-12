import { useMemo, useState, type ReactNode } from 'react';

export interface DataTableColumn<T> {
  key: keyof T & string;
  header: string;
  render?: (row: T) => ReactNode;
}

export interface DataTableProps<T extends object> {
  caption: string;
  rows: T[];
  columns: DataTableColumn<T>[];
  filterLabel?: string;
  pageSize?: number;
}

export function DataTable<T extends object>({ caption, rows, columns, filterLabel = 'Filter records', pageSize = 5 }: DataTableProps<T>) {
  const [filter, setFilter] = useState('');
  const [sortKey, setSortKey] = useState<keyof T & string>(columns[0]?.key ?? ('id' as keyof T & string));
  const [ascending, setAscending] = useState(true);

  const visibleRows = useMemo(() => {
    const normalizedFilter = filter.trim().toLowerCase();
    const filtered = normalizedFilter
      ? rows.filter((row) => Object.values(row as Record<string, unknown>).some((value) => String(value).toLowerCase().includes(normalizedFilter)))
      : rows;
    return [...filtered].sort((left, right) => {
      const comparison = String((left as Record<string, unknown>)[sortKey] ?? '').localeCompare(String((right as Record<string, unknown>)[sortKey] ?? ''));
      return ascending ? comparison : -comparison;
    }).slice(0, pageSize);
  }, [ascending, filter, pageSize, rows, sortKey]);

  function toggleSort(key: keyof T & string) {
    if (key === sortKey) {
      setAscending((current) => !current);
      return;
    }
    setSortKey(key);
    setAscending(true);
  }

  return (
    <div className="data-table-shell">
      <div className="data-table__controls">
        <label className="ds-label">
          {filterLabel}
          <input className="ds-control ds-input ds-size-md" value={filter} onChange={(event) => setFilter(event.target.value)} />
        </label>
      </div>
      <div className="data-table__scroller">
        <table className="data-table" aria-label={caption}>
          <caption className="ds-visually-hidden">{caption}</caption>
          <thead>
            <tr>{columns.map((column) => <th key={column.key} scope="col"><button type="button" onClick={() => toggleSort(column.key)}>{column.header}{sortKey === column.key ? ascending ? ' ▲' : ' ▼' : ''}</button></th>)}</tr>
          </thead>
          <tbody>
            {visibleRows.map((row, index) => (
              <tr key={String((row as Record<string, unknown>).id ?? (row as Record<string, unknown>).name ?? index)}>{columns.map((column) => <td key={column.key}>{column.render ? column.render(row) : String((row as Record<string, unknown>)[column.key] ?? '')}</td>)}</tr>
            ))}
          </tbody>
        </table>
      </div>
      <div className="data-table__pager">Showing {visibleRows.length} of {rows.length}</div>
    </div>
  );
}

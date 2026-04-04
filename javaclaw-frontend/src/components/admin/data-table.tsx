import type { ReactNode } from "react"

import { cn } from "@/lib/utils"

export interface Column<T> {
  key: string
  header: ReactNode
  cell: (row: T) => ReactNode
  width?: string
  className?: string
}

interface DataTableProps<T> {
  columns: Column<T>[]
  rows: T[]
  rowKey: (row: T) => string
  empty?: ReactNode
  loading?: boolean
}

/**
 * Dense data table per design ctx v3 §10.1:
 *  - JetBrains Mono 12px body, 9px uppercase headers.
 *  - 36px dense row height, sticky header on --surface-raised.
 *  - Row hover: --surface-card (dark) / --surface-elevated (light).
 */
export function DataTable<T>({
  columns,
  rows,
  rowKey,
  empty,
  loading,
}: DataTableProps<T>) {
  return (
    <div className="overflow-hidden rounded-sm border border-border bg-card">
      <table className="w-full border-collapse font-mono text-[12px] tabular-nums">
        <thead className="sticky top-0 z-10 bg-surface-raised">
          <tr className="border-b border-border">
            {columns.map((col) => (
              <th
                key={col.key}
                className={cn(
                  "px-3.5 py-1.5 text-left text-[9px] font-bold uppercase leading-tight tracking-[0.07em] text-muted-foreground",
                  col.className,
                )}
                style={col.width ? { width: col.width } : undefined}
              >
                {col.header}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {loading ? (
            <tr>
              <td
                colSpan={columns.length}
                className="px-3.5 py-8 text-center text-[12px] text-muted-foreground"
              >
                Loading…
              </td>
            </tr>
          ) : rows.length === 0 ? (
            <tr>
              <td
                colSpan={columns.length}
                className="px-3.5 py-10 text-center text-[12px] text-muted-foreground"
              >
                {empty ?? "No data"}
              </td>
            </tr>
          ) : (
            rows.map((row) => (
              <tr
                key={rowKey(row)}
                className="h-9 border-b border-border transition-colors duration-150 hover:bg-surface-card last:border-0"
              >
                {columns.map((col) => (
                  <td
                    key={col.key}
                    className={cn(
                      "px-3.5 align-middle text-foreground",
                      col.className,
                    )}
                  >
                    {col.cell(row)}
                  </td>
                ))}
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  )
}

import {
  IconAlertCircle,
  IconCheck,
  IconChevronRight,
  IconLoader2,
  IconTool,
} from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"

import { cn } from "@/lib/utils"

export type ToolCallStatus = "pending" | "running" | "complete" | "error"

interface ToolCallCardProps {
  name: string
  status: ToolCallStatus
  input?: unknown
  output?: unknown
  errorMessage?: string
  className?: string
}

function formatJson(value: unknown): string {
  if (value === undefined || value === null) return ""
  if (typeof value === "string") return value
  try {
    return JSON.stringify(value, null, 2)
  } catch {
    return String(value)
  }
}

/**
 * Tool-call card per design ctx v3 §10 — subtle surface-raised block inside
 * the agent message column, with kv-table aesthetic: thin border, monospace,
 * status-coded icon + label. Uses JavaClaw semantic tokens (--surface-raised,
 * --success, --info, --warning, --error) so both themes adapt automatically.
 */
export function ToolCallCard({
  name,
  status,
  input,
  output,
  errorMessage,
  className,
}: ToolCallCardProps) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(false)

  const statusLabel = {
    pending: t("chat.tool.statusPending"),
    running: t("chat.tool.statusRunning"),
    complete: t("chat.tool.statusComplete"),
    error: t("chat.tool.statusError"),
  }[status]

  const isRunning = status === "running" || status === "pending"

  return (
    <div
      data-status={status}
      className={cn(
        "relative flex w-full flex-col overflow-hidden rounded-sm border border-border bg-surface-raised",
        isRunning && "animate-[jc-pulse-subtle_1.8s_ease-in-out_infinite]",
        className,
      )}
    >
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        aria-label={expanded ? t("chat.tool.hideDetails") : t("chat.tool.showDetails")}
        className="flex items-center gap-2.5 px-3.5 py-2 text-left transition-colors hover:bg-surface-hover/20"
      >
        <IconChevronRight
          width={14}
          height={14}
          strokeWidth={1.5}
          aria-hidden
          className={cn(
            "shrink-0 text-muted-foreground transition-transform duration-150",
            expanded && "rotate-90",
          )}
        />
        <IconTool
          width={14}
          height={14}
          strokeWidth={1.5}
          aria-hidden
          className="shrink-0 text-muted-foreground"
        />
        <span className="flex-1 truncate font-mono text-[12px] text-foreground">
          {name}
        </span>
        <span
          className={cn(
            "inline-flex items-center gap-1 font-mono text-[9px] font-bold uppercase tracking-[0.04em]",
            status === "pending" && "text-muted-foreground",
            status === "running" && "text-info",
            status === "complete" && "text-success",
            status === "error" && "text-error",
          )}
        >
          {status === "running" || status === "pending" ? (
            <IconLoader2
              width={11}
              height={11}
              strokeWidth={1.5}
              aria-hidden
              className="animate-spin"
            />
          ) : status === "complete" ? (
            <IconCheck width={11} height={11} strokeWidth={1.5} aria-hidden />
          ) : (
            <IconAlertCircle
              width={11}
              height={11}
              strokeWidth={1.5}
              aria-hidden
            />
          )}
          <span>{statusLabel}</span>
        </span>
      </button>
      {expanded && (
        <div className="flex flex-col gap-3 border-t border-border px-3.5 py-3">
          {input !== undefined && (
            <div className="flex flex-col gap-1">
              <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.07em] text-muted-foreground">
                {t("chat.tool.input")}
              </span>
              <pre className="overflow-x-auto rounded-sm border border-border bg-background px-2.5 py-2 font-mono text-[12px] leading-[1.5] text-foreground">
                {formatJson(input)}
              </pre>
            </div>
          )}
          {(output !== undefined || errorMessage) && (
            <div className="flex flex-col gap-1">
              <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.07em] text-muted-foreground">
                {t("chat.tool.output")}
              </span>
              <pre
                className={cn(
                  "overflow-x-auto rounded-sm border px-2.5 py-2 font-mono text-[12px] leading-[1.5]",
                  status === "error"
                    ? "border-error/40 bg-error/10 text-error"
                    : "border-border bg-background text-foreground",
                )}
              >
                {errorMessage ?? formatJson(output)}
              </pre>
            </div>
          )}
        </div>
      )}
    </div>
  )
}

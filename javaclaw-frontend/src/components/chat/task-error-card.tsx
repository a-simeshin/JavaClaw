import {
  IconAlertTriangle,
  IconChevronRight,
  IconRefresh,
} from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"

import { cn } from "@/lib/utils"

interface TaskErrorCardProps {
  taskId: string
  taskName: string
  errorMessage: string
  errorTrace?: string
  llmRequest?: string
  durationMs?: number
  onRetry?: (taskId: string) => void
  onShowAudit?: (taskId: string) => void
  className?: string
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  const seconds = Math.round(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remaining = seconds % 60
  return remaining > 0 ? `${minutes}m ${remaining}s` : `${minutes}m`
}

export function TaskErrorCard({
  taskId,
  taskName,
  errorMessage,
  errorTrace,
  llmRequest,
  durationMs,
  onRetry,
  onShowAudit,
  className,
}: TaskErrorCardProps) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(false)

  return (
    <div
      data-role="task-error"
      className={cn(
        "relative flex w-full flex-col overflow-hidden rounded-sm border border-error/40 bg-surface-raised",
        className,
      )}
    >
      <button
        type="button"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
        aria-label={expanded ? t("chat.task.hideDetails") : t("chat.task.showDetails")}
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
        <IconAlertTriangle
          width={14}
          height={14}
          strokeWidth={1.5}
          aria-hidden
          className="shrink-0 text-error"
        />
        <span className="flex-1 truncate font-mono text-[12px] text-foreground">
          {taskName}
        </span>
        <span className="inline-flex items-center gap-1 font-mono text-[9px] font-bold uppercase tracking-[0.04em] text-error">
          {t("chat.task.status.failed")}
        </span>
      </button>

      <div className="border-t border-error/20 px-3.5 py-2">
        <p className="font-mono text-[12px] leading-[1.5] text-error">
          {errorMessage}
        </p>
      </div>

      {expanded && (
        <div className="flex flex-col gap-3 border-t border-border px-3.5 py-3">
          {errorTrace && (
            <div className="flex flex-col gap-1">
              <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.07em] text-muted-foreground">
                {t("chat.task.error.stacktrace")}
              </span>
              <pre className="max-h-48 overflow-auto rounded-sm border border-error/20 bg-error/5 px-2.5 py-2 font-mono text-[11px] leading-[1.5] text-error/80">
                {errorTrace}
              </pre>
            </div>
          )}
          {llmRequest && (
            <div className="flex flex-col gap-1">
              <span className="font-mono text-[9px] font-semibold uppercase tracking-[0.07em] text-muted-foreground">
                {t("chat.task.error.llmRequest")}
              </span>
              <pre className="max-h-48 overflow-auto rounded-sm border border-border bg-background px-2.5 py-2 font-mono text-[11px] leading-[1.5] text-foreground">
                {llmRequest}
              </pre>
            </div>
          )}
          {durationMs != null && (
            <span className="font-mono text-[9px] text-muted-foreground">
              {t("chat.task.duration")}: {formatDuration(durationMs)}
            </span>
          )}
        </div>
      )}

      <div className="flex items-center gap-2 border-t border-border px-3.5 py-2">
        {onRetry && (
          <button
            type="button"
            onClick={() => onRetry(taskId)}
            className="inline-flex items-center gap-1 rounded-sm px-2 py-1 font-mono text-[11px] text-foreground transition-colors hover:bg-surface-hover/30"
          >
            <IconRefresh width={12} height={12} strokeWidth={1.5} aria-hidden />
            {t("actions.retry")}
          </button>
        )}
        {onShowAudit && (
          <button
            type="button"
            onClick={() => onShowAudit(taskId)}
            className="font-mono text-[11px] text-accent-strong underline underline-offset-2 hover:text-accent-strong/80"
          >
            {t("chat.task.showAudit")}
          </button>
        )}
      </div>
    </div>
  )
}

import {
  IconCheck,
  IconChevronRight,
  IconClock,
  IconLoader2,
  IconX,
} from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"

import { cn } from "@/lib/utils"

export type TaskStatus =
  | "completed"
  | "failed"
  | "cancelled"
  | "in_progress"
  | "awaiting_input"
  | "todo"

interface TaskNotificationMessageProps {
  taskId: string
  taskName: string
  status: TaskStatus
  message?: string
  durationMs?: number
  timestamp?: Date | string
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

const STATUS_ICON: Record<TaskStatus, React.FC<React.SVGProps<SVGSVGElement> & { size?: number }>> = {
  completed: IconCheck,
  failed: IconX,
  cancelled: IconX,
  in_progress: IconLoader2,
  awaiting_input: IconClock,
  todo: IconClock,
}

export function TaskNotificationMessage({
  taskId,
  taskName,
  status,
  message,
  durationMs,
  timestamp,
  onShowAudit,
  className,
}: TaskNotificationMessageProps) {
  const { t } = useTranslation()
  const [expanded, setExpanded] = useState(false)

  const StatusIcon = STATUS_ICON[status]
  const isActive = status === "in_progress" || status === "awaiting_input"

  const statusLabel = t(`chat.task.status.${status}`, { defaultValue: status })

  const formattedTime =
    timestamp instanceof Date
      ? timestamp.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })
      : timestamp

  return (
    <div
      data-role="task-notification"
      data-status={status}
      className={cn(
        "relative flex w-full flex-col overflow-hidden rounded-sm border bg-surface-raised",
        status === "completed" && "border-success/40",
        status === "failed" && "border-error/40",
        status === "cancelled" && "border-border",
        isActive && "border-info/40",
        status === "todo" && "border-border",
        isActive && "animate-[jc-pulse-subtle_1.8s_ease-in-out_infinite]",
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
        <StatusIcon
          width={14}
          height={14}
          strokeWidth={1.5}
          aria-hidden
          className={cn(
            "shrink-0",
            status === "completed" && "text-success",
            status === "failed" && "text-error",
            status === "cancelled" && "text-muted-foreground",
            status === "in_progress" && "animate-spin text-info",
            status === "awaiting_input" && "text-warning-text",
            status === "todo" && "text-muted-foreground",
          )}
        />
        <span className="flex-1 truncate font-mono text-[12px] text-foreground">
          {taskName}
        </span>
        <span
          className={cn(
            "inline-flex items-center gap-1 font-mono text-[9px] font-bold uppercase tracking-[0.04em]",
            status === "completed" && "text-success",
            status === "failed" && "text-error",
            status === "cancelled" && "text-muted-foreground",
            isActive && "text-info",
            status === "todo" && "text-muted-foreground",
          )}
        >
          {statusLabel}
        </span>
        {formattedTime && (
          <span className="font-mono text-[9px] text-muted-foreground">
            {formattedTime}
          </span>
        )}
      </button>

      {expanded && (
        <div className="flex flex-col gap-2 border-t border-border px-3.5 py-3">
          {message && (
            <p className="font-mono text-[12px] leading-[1.5] text-foreground">
              {message}
            </p>
          )}
          {durationMs != null && (
            <span className="font-mono text-[9px] text-muted-foreground">
              {t("chat.task.duration")}: {formatDuration(durationMs)}
            </span>
          )}
          {onShowAudit && (
            <button
              type="button"
              onClick={(e) => {
                e.stopPropagation()
                onShowAudit(taskId)
              }}
              className="self-start font-mono text-[11px] text-accent-strong underline underline-offset-2 hover:text-accent-strong/80"
            >
              {t("chat.task.showAudit")}
            </button>
          )}
        </div>
      )}
    </div>
  )
}

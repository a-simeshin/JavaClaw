import { IconLoader2, IconPlayerStop } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"

import { cn } from "@/lib/utils"

interface TaskProgressCardProps {
  taskId: string
  taskName: string
  progressText?: string
  progressPercent?: number
  onCancel?: (taskId: string) => void
  className?: string
}

export function TaskProgressCard({
  taskId,
  taskName,
  progressText,
  progressPercent,
  onCancel,
  className,
}: TaskProgressCardProps) {
  const { t } = useTranslation()

  const isDeterminate = progressPercent != null
  const clampedPercent = isDeterminate
    ? Math.max(0, Math.min(100, progressPercent))
    : undefined

  return (
    <div
      data-role="task-progress"
      className={cn(
        "relative flex w-full flex-col overflow-hidden rounded-sm border border-info/40 bg-surface-raised",
        "animate-[jc-pulse-subtle_1.8s_ease-in-out_infinite]",
        className,
      )}
    >
      <div className="flex items-center gap-2.5 px-3.5 py-2">
        <IconLoader2
          width={14}
          height={14}
          strokeWidth={1.5}
          aria-hidden
          className="shrink-0 animate-spin text-info"
        />
        <span className="flex-1 truncate font-mono text-[12px] text-foreground">
          {taskName}
        </span>
        <span className="font-mono text-[9px] font-bold uppercase tracking-[0.04em] text-info">
          {t("chat.task.status.in_progress")}
        </span>
      </div>

      <div className="px-3.5 pb-2">
        <div
          className="h-1 w-full overflow-hidden rounded-full bg-info/20"
          role="progressbar"
          aria-valuenow={clampedPercent}
          aria-valuemin={0}
          aria-valuemax={100}
          aria-label={t("chat.task.progress.bar")}
        >
          {isDeterminate ? (
            <div
              className="h-full rounded-full bg-info transition-[width] duration-300 ease-out"
              style={{ width: `${clampedPercent}%` }}
            />
          ) : (
            <div className="h-full w-1/3 animate-[jc-indeterminate_1.5s_ease-in-out_infinite] rounded-full bg-info" />
          )}
        </div>
      </div>

      {(progressText || onCancel) && (
        <div className="flex items-center justify-between border-t border-border px-3.5 py-2">
          {progressText ? (
            <span className="font-mono text-[11px] text-muted-foreground">
              {progressText}
            </span>
          ) : (
            <span />
          )}
          {onCancel && (
            <button
              type="button"
              onClick={() => onCancel(taskId)}
              aria-label={t("chat.task.progress.cancel")}
              className="inline-flex items-center gap-1 rounded-sm px-2 py-1 font-mono text-[11px] text-error transition-colors hover:bg-error/10"
            >
              <IconPlayerStop width={12} height={12} strokeWidth={1.5} aria-hidden />
              {t("actions.cancel")}
            </button>
          )}
        </div>
      )}
    </div>
  )
}

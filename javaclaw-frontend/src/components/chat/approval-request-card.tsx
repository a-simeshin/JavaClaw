import {
  IconCheck,
  IconClock,
  IconX,
} from "@tabler/icons-react"
import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"

import { cn } from "@/lib/utils"

export interface ApprovalRequestCardProps {
  approvalId: string
  taskName: string
  question: string
  timeoutAt: string | Date
  resolved?: "approved" | "denied" | "timeout" | null
  isSubmitting?: boolean
  onApprove?: () => void
  onDeny?: () => void
  onRespond?: (text: string) => void
  onShowAudit?: (approvalId: string) => void
  className?: string
}

function computeProgress(timeoutAt: Date, createdAt: Date, now: number): number {
  const total = timeoutAt.getTime() - createdAt.getTime()
  if (total <= 0) return 0
  const remaining = timeoutAt.getTime() - now
  return Math.max(0, Math.min(100, (remaining / total) * 100))
}

function formatRemaining(ms: number): string {
  if (ms <= 0) return "0s"
  const seconds = Math.ceil(ms / 1000)
  if (seconds < 60) return `${seconds}s`
  const minutes = Math.floor(seconds / 60)
  const remaining = seconds % 60
  return remaining > 0 ? `${minutes}m ${remaining}s` : `${minutes}m`
}

export function ApprovalRequestCard({
  approvalId,
  taskName,
  question,
  timeoutAt,
  resolved = null,
  isSubmitting = false,
  onApprove,
  onDeny,
  onRespond,
  onShowAudit,
  className,
}: ApprovalRequestCardProps) {
  const { t } = useTranslation()
  const [replyText, setReplyText] = useState("")
  const [now, setNow] = useState(Date.now())
  const createdAtRef = useRef(new Date())

  const deadline = timeoutAt instanceof Date ? timeoutAt : new Date(timeoutAt)
  const remaining = deadline.getTime() - now
  const isExpired = remaining <= 0
  const progress = computeProgress(deadline, createdAtRef.current, now)

  // Countdown timer — tick every second while not resolved
  useEffect(() => {
    if (resolved) return
    const interval = setInterval(() => setNow(Date.now()), 1000)
    return () => clearInterval(interval)
  }, [resolved])

  const isDisabled = !!resolved || isSubmitting || isExpired
  const effectiveResolved = resolved ?? (isExpired ? "timeout" : null)

  const barColor =
    progress > 50
      ? "bg-success"
      : progress > 20
        ? "bg-warning"
        : "bg-error"

  const handleSendReply = () => {
    const text = replyText.trim()
    if (!text || isDisabled) return
    onRespond?.(text)
    setReplyText("")
  }

  return (
    <div
      data-role="approval-request"
      data-status={effectiveResolved ?? "pending"}
      className={cn(
        "relative flex w-full flex-col overflow-hidden rounded-sm border bg-surface-raised",
        effectiveResolved === "approved" && "border-success/40",
        effectiveResolved === "denied" && "border-error/40",
        effectiveResolved === "timeout" && "border-border",
        !effectiveResolved && "border-warning/40",
        className,
      )}
    >
      {/* Header */}
      <div className="flex items-center gap-2.5 px-3.5 py-2">
        <IconClock
          width={14}
          height={14}
          strokeWidth={1.5}
          aria-hidden
          className="shrink-0 text-warning-text"
        />
        <span className="flex-1 truncate font-mono text-[12px] text-foreground">
          {taskName}
        </span>
        <span className="font-mono text-[9px] font-bold uppercase tracking-[0.04em] text-warning-text">
          {t("chat.task.status.awaiting_input")}
        </span>
      </div>

      {/* Question */}
      <div className="border-t border-border px-3.5 py-3">
        <p className="font-mono text-[13px] leading-[1.5] text-foreground">
          {question}
        </p>
      </div>

      {/* Countdown bar */}
      {!effectiveResolved && (
        <div className="px-3.5 pb-2">
          <div className="flex items-center justify-between pb-1">
            <span className="font-mono text-[9px] text-muted-foreground">
              {t("chat.approval.timeRemaining")}
            </span>
            <span className="font-mono text-[9px] text-muted-foreground">
              {formatRemaining(remaining)}
            </span>
          </div>
          <div
            className="h-1 w-full overflow-hidden rounded-full bg-border/40"
            role="progressbar"
            aria-valuenow={Math.round(progress)}
            aria-valuemin={0}
            aria-valuemax={100}
            aria-label={t("chat.approval.countdown")}
          >
            <div
              className={cn("h-full rounded-full transition-[width] duration-1000 ease-linear", barColor)}
              style={{ width: `${progress}%` }}
            />
          </div>
        </div>
      )}

      {/* Action area */}
      <div className="border-t border-border px-3.5 py-2">
        {effectiveResolved ? (
          /* Resolved pill */
          <div className="flex items-center gap-1.5">
            {effectiveResolved === "approved" && (
              <span className="inline-flex items-center gap-1 rounded-full bg-success/10 px-2.5 py-1 font-mono text-[11px] font-semibold text-success">
                <IconCheck width={12} height={12} strokeWidth={2} aria-hidden />
                {t("chat.approval.approved")}
              </span>
            )}
            {effectiveResolved === "denied" && (
              <span className="inline-flex items-center gap-1 rounded-full bg-error/10 px-2.5 py-1 font-mono text-[11px] font-semibold text-error">
                <IconX width={12} height={12} strokeWidth={2} aria-hidden />
                {t("chat.approval.denied")}
              </span>
            )}
            {effectiveResolved === "timeout" && (
              <span className="inline-flex items-center gap-1 rounded-full bg-border/40 px-2.5 py-1 font-mono text-[11px] font-semibold text-muted-foreground">
                <IconClock width={12} height={12} strokeWidth={2} aria-hidden />
                {t("chat.approval.expired")}
              </span>
            )}
          </div>
        ) : (
          /* Action buttons + quick reply */
          <div className="flex flex-col gap-2">
            <div className="flex items-center gap-2">
              <button
                type="button"
                disabled={isDisabled}
                onClick={onApprove}
                className="inline-flex items-center gap-1 rounded-sm bg-success/10 px-3 py-1.5 font-mono text-[11px] font-semibold text-success transition-colors hover:bg-success/20 disabled:opacity-40 disabled:pointer-events-none"
              >
                <IconCheck width={12} height={12} strokeWidth={2} aria-hidden />
                {t("chat.approval.approve")}
              </button>
              <button
                type="button"
                disabled={isDisabled}
                onClick={onDeny}
                className="inline-flex items-center gap-1 rounded-sm bg-error/10 px-3 py-1.5 font-mono text-[11px] font-semibold text-error transition-colors hover:bg-error/20 disabled:opacity-40 disabled:pointer-events-none"
              >
                <IconX width={12} height={12} strokeWidth={2} aria-hidden />
                {t("chat.approval.deny")}
              </button>
              {onShowAudit && (
                <button
                  type="button"
                  onClick={() => onShowAudit(approvalId)}
                  className="ml-auto font-mono text-[11px] text-accent-strong underline underline-offset-2 hover:text-accent-strong/80"
                >
                  {t("chat.task.showAudit")}
                </button>
              )}
            </div>
            {onRespond && (
              <div className="flex items-center gap-1.5">
                <input
                  type="text"
                  value={replyText}
                  onChange={(e) => setReplyText(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && !e.shiftKey) {
                      e.preventDefault()
                      handleSendReply()
                    }
                  }}
                  disabled={isDisabled}
                  placeholder={t("chat.approval.replyPlaceholder")}
                  className="flex-1 rounded-sm border border-border bg-background px-2 py-1 font-mono text-[11px] text-foreground placeholder:text-muted-foreground focus:border-accent-strong focus:outline-none disabled:opacity-40"
                />
                <button
                  type="button"
                  disabled={isDisabled || !replyText.trim()}
                  onClick={handleSendReply}
                  className="rounded-sm px-2 py-1 font-mono text-[11px] text-accent-strong transition-colors hover:bg-accent-strong/10 disabled:opacity-40 disabled:pointer-events-none"
                >
                  {t("actions.send")}
                </button>
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

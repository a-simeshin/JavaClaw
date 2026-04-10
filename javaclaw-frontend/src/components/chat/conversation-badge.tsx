import { cn } from "@/lib/utils"

interface ConversationBadgeProps {
  unreadCount?: number
  hasPendingApproval?: boolean
  hasActiveTask?: boolean
  className?: string
}

export function ConversationBadge({
  unreadCount = 0,
  hasPendingApproval = false,
  hasActiveTask = false,
  className,
}: ConversationBadgeProps) {
  if (unreadCount <= 0 && !hasPendingApproval && !hasActiveTask) return null

  return (
    <span
      data-role="conversation-badge"
      className={cn("inline-flex items-center gap-1", className)}
    >
      {/* Active task — pulsing blue dot */}
      {hasActiveTask && (
        <span
          data-indicator="active-task"
          className="relative flex h-2 w-2"
          aria-label="Active task"
        >
          <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-info opacity-60" />
          <span className="relative inline-flex h-2 w-2 rounded-full bg-info" />
        </span>
      )}

      {/* Pending approval — orange dot */}
      {hasPendingApproval && (
        <span
          data-indicator="pending-approval"
          className="inline-flex h-2 w-2 rounded-full bg-warning"
          aria-label="Pending approval"
        />
      )}

      {/* Unread notification count */}
      {unreadCount > 0 && (
        <span
          data-indicator="unread-count"
          className="inline-flex min-w-[16px] items-center justify-center rounded-full bg-error px-1 py-0.5 font-mono text-[9px] font-bold leading-none text-white"
        >
          {unreadCount > 99 ? "99+" : unreadCount}
        </span>
      )}
    </span>
  )
}

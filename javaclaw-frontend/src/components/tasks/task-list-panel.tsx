import {
  IconCheck,
  IconChevronRight,
  IconClock,
  IconLoader2,
  IconPlayerStop,
  IconRefresh,
  IconTrash,
  IconX,
} from "@tabler/icons-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import {
  cancelTask,
  deleteTask,
  getChildTasks,
  listTasks,
  type TaskDto,
  type TaskStatus,
} from "@/api/tasks"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import { ScrollArea } from "@/components/ui/scroll-area"
import { cn } from "@/lib/utils"

type TabFilter = "all" | "active" | "recurring" | "completed" | "failed"

const TAB_FILTERS: TabFilter[] = ["all", "active", "recurring", "completed", "failed"]

const STATUS_ICON: Record<string, React.FC<React.SVGProps<SVGSVGElement> & { size?: number }>> = {
  completed: IconCheck,
  failed: IconX,
  cancelled: IconX,
  in_progress: IconLoader2,
  awaiting_human_input: IconClock,
  todo: IconClock,
}

function statusColor(status: TaskStatus | null): string {
  switch (status) {
    case "completed":
      return "text-success"
    case "failed":
      return "text-error"
    case "cancelled":
      return "text-muted-foreground"
    case "in_progress":
      return "text-info"
    case "awaiting_human_input":
      return "text-warning-text"
    default:
      return "text-muted-foreground"
  }
}

function formatRelativeTime(dateStr: string | null): string {
  if (!dateStr) return ""
  const date = new Date(dateStr)
  const now = new Date()
  const diffMs = now.getTime() - date.getTime()
  const diffMin = Math.floor(diffMs / 60000)
  if (diffMin < 1) return "<1m"
  if (diffMin < 60) return `${diffMin}m`
  const diffHours = Math.floor(diffMin / 60)
  if (diffHours < 24) return `${diffHours}h`
  const diffDays = Math.floor(diffHours / 24)
  return `${diffDays}d`
}

interface TaskListPanelProps {
  userId?: string
  onSelectTask?: (taskId: string) => void
  className?: string
}

export function TaskListPanel({
  userId,
  onSelectTask,
  className,
}: TaskListPanelProps) {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<TabFilter>("all")
  const [tasks, setTasks] = useState<TaskDto[]>([])
  const [childrenMap, setChildrenMap] = useState<Record<string, TaskDto[]>>({})
  const [expandedParents, setExpandedParents] = useState<Set<string>>(new Set())
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const fetchTasks = useCallback(async () => {
    setLoading(true)
    setError(null)
    try {
      const statusFilter = tabToStatus(activeTab)
      const result = await listTasks({ userId, status: statusFilter ?? undefined })
      setTasks(result)
    } catch (e) {
      setError(e instanceof Error ? e.message : t("errors.unknown"))
    } finally {
      setLoading(false)
    }
  }, [activeTab, userId, t])

  useEffect(() => {
    fetchTasks()
  }, [fetchTasks])

  const handleToggleChildren = useCallback(
    async (parentId: string) => {
      setExpandedParents((prev) => {
        const next = new Set(prev)
        if (next.has(parentId)) {
          next.delete(parentId)
        } else {
          next.add(parentId)
        }
        return next
      })
      if (!childrenMap[parentId]) {
        try {
          const children = await getChildTasks(parentId)
          setChildrenMap((prev) => ({ ...prev, [parentId]: children }))
        } catch {
          // silently ignore child fetch errors
        }
      }
    },
    [childrenMap],
  )

  const handleCancel = useCallback(
    async (taskId: string, e: React.MouseEvent) => {
      e.stopPropagation()
      try {
        await cancelTask(taskId)
        await fetchTasks()
      } catch {
        // handled by refresh
      }
    },
    [fetchTasks],
  )

  const handleDelete = useCallback(
    async (taskId: string, e: React.MouseEvent) => {
      e.stopPropagation()
      try {
        await deleteTask(taskId)
        await fetchTasks()
      } catch {
        // handled by refresh
      }
    },
    [fetchTasks],
  )

  // Filter top-level tasks (no parentTaskId)
  const topLevelTasks = tasks.filter((task) => !task.parentTaskId)

  return (
    <div
      data-role="task-list-panel"
      className={cn("flex h-full flex-col", className)}
    >
      {/* Tabs */}
      <div
        role="tablist"
        aria-label={t("tasks.title")}
        className="flex shrink-0 gap-1 border-b border-border px-3 py-2"
      >
        {TAB_FILTERS.map((tab) => (
          <button
            key={tab}
            role="tab"
            aria-selected={activeTab === tab}
            onClick={() => setActiveTab(tab)}
            className={cn(
              "rounded-sm px-2.5 py-1 font-mono text-[11px] font-medium transition-colors",
              activeTab === tab
                ? "bg-accent text-accent-foreground"
                : "text-muted-foreground hover:bg-surface-hover/40 hover:text-foreground",
            )}
          >
            {t(`tasks.tab.${tab}`)}
          </button>
        ))}

        <Button
          variant="ghost"
          size="icon"
          className="ml-auto h-6 w-6"
          onClick={() => fetchTasks()}
          aria-label={t("tasks.refresh")}
        >
          <IconRefresh width={14} height={14} strokeWidth={1.5} />
        </Button>
      </div>

      {/* Content */}
      <ScrollArea className="flex-1">
        {loading && tasks.length === 0 && (
          <div className="flex items-center justify-center py-8">
            <IconLoader2
              width={20}
              height={20}
              className="animate-spin text-muted-foreground"
            />
          </div>
        )}

        {error && (
          <div className="px-3 py-4 text-center font-mono text-[11px] text-error">
            {error}
          </div>
        )}

        {!loading && !error && topLevelTasks.length === 0 && (
          <div className="px-3 py-8 text-center font-mono text-[11px] text-muted-foreground">
            {t("tasks.empty")}
          </div>
        )}

        <div className="flex flex-col">
          {topLevelTasks.map((task) => (
            <TaskRow
              key={task.id}
              task={task}
              children={childrenMap[task.id]}
              expanded={expandedParents.has(task.id)}
              onToggleChildren={handleToggleChildren}
              onSelect={onSelectTask}
              onCancel={handleCancel}
              onDelete={handleDelete}
              depth={0}
            />
          ))}
        </div>
      </ScrollArea>
    </div>
  )
}

interface TaskRowProps {
  task: TaskDto
  children?: TaskDto[]
  expanded?: boolean
  onToggleChildren?: (parentId: string) => void
  onSelect?: (taskId: string) => void
  onCancel?: (taskId: string, e: React.MouseEvent) => void
  onDelete?: (taskId: string, e: React.MouseEvent) => void
  depth: number
}

function TaskRow({
  task,
  children: childTasks,
  expanded,
  onToggleChildren,
  onSelect,
  onCancel,
  onDelete,
  depth,
}: TaskRowProps) {
  const { t } = useTranslation()
  const StatusIcon = STATUS_ICON[task.status ?? "todo"] ?? IconClock
  const hasChildren = task.runtimeType !== "cron" && depth === 0
  const isActive = task.status === "in_progress" || task.status === "awaiting_human_input"
  const isTerminal = task.status === "completed" || task.status === "failed" || task.status === "cancelled"

  return (
    <>
      <div
        role="row"
        data-task-id={task.id}
        onClick={() => onSelect?.(task.id)}
        className={cn(
          "group flex cursor-pointer items-center gap-2 border-b border-border/50 px-3 py-2 transition-colors hover:bg-surface-hover/20",
          depth > 0 && "bg-surface-raised/30",
        )}
        style={{ paddingLeft: `${12 + depth * 16}px` }}
      >
        {/* Expand toggle for parent tasks */}
        {hasChildren ? (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation()
              onToggleChildren?.(task.id)
            }}
            className="shrink-0 text-muted-foreground transition-transform"
            aria-label={t("tasks.toggleChildren")}
          >
            <IconChevronRight
              width={12}
              height={12}
              strokeWidth={1.5}
              className={cn("transition-transform", expanded && "rotate-90")}
            />
          </button>
        ) : (
          <span className="w-3 shrink-0" />
        )}

        {/* Status icon */}
        <StatusIcon
          width={14}
          height={14}
          strokeWidth={1.5}
          aria-hidden
          className={cn(
            "shrink-0",
            statusColor(task.status),
            task.status === "in_progress" && "animate-spin",
          )}
        />

        {/* Task name */}
        <span className="flex-1 truncate font-mono text-[12px] text-foreground">
          {task.name}
        </span>

        {/* Runtime badge */}
        {task.runtimeType === "cron" && (
          <Badge variant="outline" className="h-4 px-1.5 text-[9px]">
            {t("tasks.runtime.cron")}
          </Badge>
        )}

        {/* Status badge */}
        <Badge
          variant={isActive ? "default" : "secondary"}
          className={cn(
            "h-4 px-1.5 text-[9px]",
            task.status === "completed" && "bg-success/10 text-success",
            task.status === "failed" && "bg-error/10 text-error",
          )}
        >
          {t(`chat.task.status.${task.status ?? "todo"}`, { defaultValue: task.status ?? "todo" })}
        </Badge>

        {/* Time */}
        <span className="font-mono text-[9px] text-muted-foreground">
          {formatRelativeTime(task.updatedAt ?? task.createdAt)}
        </span>

        {/* Actions (visible on hover) */}
        <div className="flex shrink-0 gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
          {isActive && (
            <button
              type="button"
              onClick={(e) => onCancel?.(task.id, e)}
              className="rounded-sm p-0.5 text-muted-foreground hover:text-error"
              aria-label={t("actions.cancel")}
            >
              <IconPlayerStop width={12} height={12} strokeWidth={1.5} />
            </button>
          )}
          {isTerminal && (
            <button
              type="button"
              onClick={(e) => onDelete?.(task.id, e)}
              className="rounded-sm p-0.5 text-muted-foreground hover:text-error"
              aria-label={t("actions.delete")}
            >
              <IconTrash width={12} height={12} strokeWidth={1.5} />
            </button>
          )}
        </div>
      </div>

      {/* Child tasks */}
      {expanded &&
        childTasks?.map((child) => (
          <TaskRow
            key={child.id}
            task={child}
            onSelect={onSelect}
            onCancel={onCancel}
            onDelete={onDelete}
            depth={depth + 1}
          />
        ))}
    </>
  )
}

function tabToStatus(tab: TabFilter): TaskStatus | null {
  switch (tab) {
    case "active":
      return "in_progress"
    case "completed":
      return "completed"
    case "failed":
      return "failed"
    default:
      return null
  }
}

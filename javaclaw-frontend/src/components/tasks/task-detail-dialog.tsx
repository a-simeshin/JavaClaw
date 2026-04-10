import {
  IconCheck,
  IconChevronDown,
  IconChevronRight,
  IconClock,
  IconLoader2,
  IconSend,
  IconTool,
  IconTruck,
  IconX,
} from "@tabler/icons-react"
import { useCallback, useEffect, useState } from "react"
import { useTranslation } from "react-i18next"

import { getDeliveryAudit, getTaskAudit, getTaskExecutions } from "@/api/audit"
import type {
  DeliveryAuditLogDto,
  TaskAuditLogDto,
  TaskExecutionDto,
} from "@/api/audit"
import { getTask } from "@/api/tasks"
import type { TaskDto } from "@/api/tasks"
import { Badge } from "@/components/ui/badge"
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { ScrollArea } from "@/components/ui/scroll-area"
import { cn } from "@/lib/utils"

type ActiveTab = "timeline" | "model" | "tools" | "deliveries"

const TABS: ActiveTab[] = ["timeline", "model", "tools", "deliveries"]

const TAB_ICONS: Record<ActiveTab, React.ReactNode> = {
  timeline: <IconClock size={14} />,
  model: <IconSend size={14} />,
  tools: <IconTool size={14} />,
  deliveries: <IconTruck size={14} />,
}

const TAB_I18N: Record<ActiveTab, string> = {
  timeline: "tasks.detail.timeline",
  model: "tasks.detail.modelRequest",
  tools: "tasks.detail.tools",
  deliveries: "tasks.detail.deliveries",
}

interface TaskDetailDialogProps {
  taskId: string | null
  open: boolean
  onOpenChange: (open: boolean) => void
}

export function TaskDetailDialog({
  taskId,
  open,
  onOpenChange,
}: TaskDetailDialogProps) {
  const { t } = useTranslation()
  const [activeTab, setActiveTab] = useState<ActiveTab>("timeline")
  const [task, setTask] = useState<TaskDto | null>(null)
  const [auditLogs, setAuditLogs] = useState<TaskAuditLogDto[]>([])
  const [executions, setExecutions] = useState<TaskExecutionDto[]>([])
  const [deliveries, setDeliveries] = useState<DeliveryAuditLogDto[]>([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const loadData = useCallback(async (id: string) => {
    setLoading(true)
    setError(null)
    try {
      const [taskData, auditData, execData, deliveryData] = await Promise.all([
        getTask(id),
        getTaskAudit(id),
        getTaskExecutions(id),
        getDeliveryAudit(id),
      ])
      setTask(taskData)
      setAuditLogs(auditData)
      setExecutions(execData)
      setDeliveries(deliveryData)
    } catch (e) {
      setError(e instanceof Error ? e.message : "Failed to load task details")
    } finally {
      setLoading(false)
    }
  }, [])

  useEffect(() => {
    if (open && taskId) {
      setActiveTab("timeline")
      loadData(taskId)
    }
  }, [open, taskId, loadData])

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent
        className="sm:max-w-2xl max-h-[80vh] flex flex-col"
        data-role="task-detail-dialog"
      >
        <DialogHeader>
          <DialogTitle>
            {task ? task.name : t("tasks.detail.title")}
          </DialogTitle>
          {task && (
            <div className="flex items-center gap-2 text-xs text-muted-foreground">
              <StatusBadge status={task.status} />
              {task.runtimeType && (
                <Badge variant="outline" className="text-xs">
                  {task.runtimeType}
                </Badge>
              )}
              {task.createdAt && (
                <span>{new Date(task.createdAt).toLocaleString()}</span>
              )}
              {task.durationMs != null && (
                <span>
                  {t("tasks.detail.duration")}: {formatDuration(task.durationMs)}
                </span>
              )}
            </div>
          )}
        </DialogHeader>

        {/* Tabs */}
        <div className="flex gap-1 border-b" role="tablist">
          {TABS.map((tab) => (
            <button
              key={tab}
              role="tab"
              aria-selected={activeTab === tab}
              className={cn(
                "flex items-center gap-1 px-3 py-1.5 text-xs font-medium border-b-2 -mb-px transition-colors",
                activeTab === tab
                  ? "border-primary text-primary"
                  : "border-transparent text-muted-foreground hover:text-foreground"
              )}
              onClick={() => setActiveTab(tab)}
            >
              {TAB_ICONS[tab]}
              {t(TAB_I18N[tab])}
            </button>
          ))}
        </div>

        {/* Content */}
        <ScrollArea className="flex-1 min-h-0">
          <div className="pr-4">
            {loading && (
              <div className="flex items-center justify-center py-8">
                <IconLoader2 className="animate-spin text-muted-foreground" size={24} />
              </div>
            )}
            {error && (
              <div className="text-sm text-destructive py-4" data-role="error">
                {error}
              </div>
            )}
            {!loading && !error && (
              <>
                {activeTab === "timeline" && (
                  <TimelineTab auditLogs={auditLogs} t={t} />
                )}
                {activeTab === "model" && (
                  <ModelTab executions={executions} t={t} />
                )}
                {activeTab === "tools" && (
                  <ToolsTab auditLogs={auditLogs} t={t} />
                )}
                {activeTab === "deliveries" && (
                  <DeliveriesTab deliveries={deliveries} t={t} />
                )}
              </>
            )}
          </div>
        </ScrollArea>
      </DialogContent>
    </Dialog>
  )
}

/* ---------- Sub-components ---------- */

function StatusBadge({ status }: { status: string | null }) {
  const colorMap: Record<string, string> = {
    completed: "bg-green-100 text-green-700 dark:bg-green-900 dark:text-green-300",
    failed: "bg-red-100 text-red-700 dark:bg-red-900 dark:text-red-300",
    cancelled: "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400",
    in_progress: "bg-blue-100 text-blue-700 dark:bg-blue-900 dark:text-blue-300",
    awaiting_human_input: "bg-orange-100 text-orange-700 dark:bg-orange-900 dark:text-orange-300",
    todo: "bg-gray-100 text-gray-600 dark:bg-gray-800 dark:text-gray-400",
  }
  return (
    <span
      className={cn(
        "inline-flex items-center px-1.5 py-0.5 rounded text-xs font-medium",
        colorMap[status ?? ""] ?? "bg-gray-100 text-gray-600"
      )}
      data-status={status}
    >
      {status ?? "unknown"}
    </span>
  )
}

/* --- Timeline Tab --- */

const EVENT_ICONS: Record<string, React.ReactNode> = {
  created: <IconClock size={14} className="text-blue-500" />,
  started: <IconLoader2 size={14} className="text-blue-500" />,
  completed: <IconCheck size={14} className="text-green-500" />,
  failed: <IconX size={14} className="text-red-500" />,
  cancelled: <IconX size={14} className="text-gray-500" />,
  tool_call: <IconTool size={14} className="text-purple-500" />,
  approval_requested: <IconClock size={14} className="text-orange-500" />,
  approval_received: <IconCheck size={14} className="text-orange-500" />,
  progress: <IconLoader2 size={14} className="text-blue-400" />,
  timeout: <IconClock size={14} className="text-red-500" />,
  delivered: <IconTruck size={14} className="text-green-500" />,
}

function TimelineTab({
  auditLogs,
  t,
}: {
  auditLogs: TaskAuditLogDto[]
  t: (key: string) => string
}) {
  if (auditLogs.length === 0) {
    return (
      <div className="text-sm text-muted-foreground py-4">
        {t("tasks.detail.noEvents")}
      </div>
    )
  }

  return (
    <div className="space-y-0" data-role="timeline">
      {auditLogs.map((log) => (
        <TimelineEvent key={log.id} log={log} t={t} />
      ))}
    </div>
  )
}

function TimelineEvent({
  log,
  t,
}: {
  log: TaskAuditLogDto
  t: (key: string) => string
}) {
  const [expanded, setExpanded] = useState(false)
  const hasDetails =
    log.errorMessage || log.toolName || log.llmRequest || log.llmResponse

  return (
    <div
      className="flex gap-3 py-2 border-b border-border/50 last:border-0"
      data-event-type={log.eventType}
    >
      {/* Icon column */}
      <div className="flex flex-col items-center pt-0.5">
        {EVENT_ICONS[log.eventType] ?? <IconClock size={14} className="text-gray-400" />}
        <div className="flex-1 w-px bg-border/50 mt-1" />
      </div>

      {/* Content */}
      <div className="flex-1 min-w-0">
        <div className="flex items-center gap-2">
          <span className="text-xs font-medium">{log.eventType}</span>
          {log.durationMs != null && (
            <Badge variant="outline" className="text-xs px-1 py-0">
              {formatDuration(log.durationMs)}
            </Badge>
          )}
          <span className="text-xs text-muted-foreground ml-auto">
            {new Date(log.createdAt).toLocaleTimeString()}
          </span>
          {hasDetails && (
            <button
              onClick={() => setExpanded(!expanded)}
              className="text-muted-foreground hover:text-foreground"
              aria-label={expanded ? "collapse" : "expand"}
            >
              {expanded ? (
                <IconChevronDown size={14} />
              ) : (
                <IconChevronRight size={14} />
              )}
            </button>
          )}
        </div>

        {/* Error summary */}
        {log.errorMessage && !expanded && (
          <p className="text-xs text-destructive mt-0.5 truncate">
            {log.errorMessage}
          </p>
        )}

        {/* Tool summary */}
        {log.toolName && !expanded && (
          <p className="text-xs text-muted-foreground mt-0.5">
            {t("tasks.detail.toolName")}: {log.toolName}
          </p>
        )}

        {/* Expanded details */}
        {expanded && (
          <div className="mt-2 space-y-2">
            {log.errorMessage && (
              <DetailBlock label="Error" content={log.errorMessage} variant="error" />
            )}
            {log.errorTrace && (
              <DetailBlock label="Stacktrace" content={log.errorTrace} variant="error" />
            )}
            {log.toolName && (
              <div className="space-y-1">
                <DetailBlock
                  label={t("tasks.detail.toolName")}
                  content={log.toolName}
                />
                {log.toolArgs && (
                  <DetailBlock
                    label={t("tasks.detail.toolArgs")}
                    content={log.toolArgs}
                    code
                  />
                )}
                {log.toolResult && (
                  <DetailBlock
                    label={t("tasks.detail.toolResult")}
                    content={log.toolResult}
                    code
                  />
                )}
              </div>
            )}
            {log.llmRequest && (
              <DetailBlock
                label={t("tasks.detail.modelRequest")}
                content={log.llmRequest}
                code
              />
            )}
            {log.llmResponse && (
              <DetailBlock
                label={t("tasks.detail.modelResponse")}
                content={log.llmResponse}
                code
              />
            )}
            {log.tokenUsage && (
              <DetailBlock label="Token usage" content={log.tokenUsage} code />
            )}
          </div>
        )}
      </div>
    </div>
  )
}

/* --- Model Tab --- */

function ModelTab({
  executions,
  t,
}: {
  executions: TaskExecutionDto[]
  t: (key: string) => string
}) {
  if (executions.length === 0) {
    return (
      <div className="text-sm text-muted-foreground py-4">
        {t("tasks.detail.noEvents")}
      </div>
    )
  }

  return (
    <div className="space-y-4" data-role="model-tab">
      {executions.map((exec) => (
        <ExecutionCard key={exec.id} execution={exec} t={t} />
      ))}
    </div>
  )
}

function ExecutionCard({
  execution,
  t,
}: {
  execution: TaskExecutionDto
  t: (key: string) => string
}) {
  const [section, setSection] = useState<"request" | "response" | null>(null)

  return (
    <div className="border rounded-lg p-3 space-y-2" data-execution-id={execution.id}>
      <div className="flex items-center gap-2 text-xs">
        <Badge variant="outline">#{execution.executionNumber}</Badge>
        <StatusBadge status={execution.status} />
        {execution.durationMs != null && (
          <span className="text-muted-foreground">
            {formatDuration(execution.durationMs)}
          </span>
        )}
        <span className="text-muted-foreground ml-auto">
          {new Date(execution.startedAt).toLocaleString()}
        </span>
      </div>

      {execution.errorMessage && (
        <p className="text-xs text-destructive">{execution.errorMessage}</p>
      )}

      <div className="flex gap-1">
        <button
          className={cn(
            "text-xs px-2 py-1 rounded",
            section === "request"
              ? "bg-primary/10 text-primary"
              : "text-muted-foreground hover:text-foreground"
          )}
          onClick={() => setSection(section === "request" ? null : "request")}
        >
          {t("tasks.detail.modelRequest")}
        </button>
        <button
          className={cn(
            "text-xs px-2 py-1 rounded",
            section === "response"
              ? "bg-primary/10 text-primary"
              : "text-muted-foreground hover:text-foreground"
          )}
          onClick={() => setSection(section === "response" ? null : "response")}
        >
          {t("tasks.detail.modelResponse")}
        </button>
      </div>

      {section === "request" && (
        <div className="space-y-2">
          {execution.systemPrompt && (
            <DetailBlock label="System prompt" content={execution.systemPrompt} code />
          )}
          {execution.userPrompt && (
            <DetailBlock label="User prompt" content={execution.userPrompt} code />
          )}
        </div>
      )}
      {section === "response" && execution.llmResponse && (
        <DetailBlock label={t("tasks.detail.modelResponse")} content={execution.llmResponse} code />
      )}
    </div>
  )
}

/* --- Tools Tab --- */

function ToolsTab({
  auditLogs,
  t,
}: {
  auditLogs: TaskAuditLogDto[]
  t: (key: string) => string
}) {
  const toolLogs = auditLogs.filter((l) => l.toolName)

  if (toolLogs.length === 0) {
    return (
      <div className="text-sm text-muted-foreground py-4">
        {t("tasks.detail.noEvents")}
      </div>
    )
  }

  return (
    <div className="space-y-2" data-role="tools-tab">
      {toolLogs.map((log) => (
        <ToolCallRow key={log.id} log={log} t={t} />
      ))}
    </div>
  )
}

function ToolCallRow({
  log,
  t,
}: {
  log: TaskAuditLogDto
  t: (key: string) => string
}) {
  const [expanded, setExpanded] = useState(false)

  return (
    <div className="border rounded-lg p-2 text-xs" data-tool-name={log.toolName}>
      <div
        className="flex items-center gap-2 cursor-pointer"
        onClick={() => setExpanded(!expanded)}
      >
        <IconTool size={14} className="text-purple-500" />
        <span className="font-medium">{log.toolName}</span>
        {log.toolDurationMs != null && (
          <Badge variant="outline" className="text-xs px-1 py-0">
            {formatDuration(log.toolDurationMs)}
          </Badge>
        )}
        <span className="text-muted-foreground ml-auto">
          {new Date(log.createdAt).toLocaleTimeString()}
        </span>
        {expanded ? <IconChevronDown size={14} /> : <IconChevronRight size={14} />}
      </div>

      {expanded && (
        <div className="mt-2 space-y-1">
          {log.toolArgs && (
            <DetailBlock label={t("tasks.detail.toolArgs")} content={log.toolArgs} code />
          )}
          {log.toolResult && (
            <DetailBlock label={t("tasks.detail.toolResult")} content={log.toolResult} code />
          )}
        </div>
      )}
    </div>
  )
}

/* --- Deliveries Tab --- */

function DeliveriesTab({
  deliveries,
  t,
}: {
  deliveries: DeliveryAuditLogDto[]
  t: (key: string) => string
}) {
  if (deliveries.length === 0) {
    return (
      <div className="text-sm text-muted-foreground py-4">
        {t("tasks.detail.noEvents")}
      </div>
    )
  }

  return (
    <div className="space-y-2" data-role="deliveries-tab">
      {deliveries.map((d) => (
        <div
          key={d.id}
          className="border rounded-lg p-2 text-xs flex items-center gap-2"
          data-delivery-id={d.id}
        >
          <IconTruck
            size={14}
            className={d.status === "delivered" ? "text-green-500" : "text-red-500"}
          />
          <span className="font-medium">{d.channelName}</span>
          <StatusBadge status={d.status} />
          <span className="text-muted-foreground">
            {t("tasks.detail.attempts")}: {d.attempts}
          </span>
          {d.durationMs != null && (
            <span className="text-muted-foreground">
              {formatDuration(d.durationMs)}
            </span>
          )}
          <span className="text-muted-foreground ml-auto">
            {new Date(d.createdAt).toLocaleTimeString()}
          </span>
          {d.errorMessage && (
            <span className="text-destructive truncate max-w-48" title={d.errorMessage}>
              {d.errorMessage}
            </span>
          )}
        </div>
      ))}
    </div>
  )
}

/* --- Shared --- */

function DetailBlock({
  label,
  content,
  code,
  variant,
}: {
  label: string
  content: string
  code?: boolean
  variant?: "error"
}) {
  return (
    <div>
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      <pre
        className={cn(
          "text-xs mt-0.5 p-2 rounded whitespace-pre-wrap break-all max-h-48 overflow-auto",
          code && "bg-muted font-mono",
          variant === "error" && "bg-red-50 text-red-700 dark:bg-red-950 dark:text-red-300",
          !code && !variant && "bg-muted"
        )}
      >
        {content}
      </pre>
    </div>
  )
}

function formatDuration(ms: number): string {
  if (ms < 1000) return `${ms}ms`
  if (ms < 60000) return `${(ms / 1000).toFixed(1)}s`
  const min = Math.floor(ms / 60000)
  const sec = Math.floor((ms % 60000) / 1000)
  return `${min}m ${sec}s`
}

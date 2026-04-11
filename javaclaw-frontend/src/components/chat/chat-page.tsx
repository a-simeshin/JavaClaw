import type { Message } from "@ai-sdk/react"
import { useAtomValue } from "jotai"
import { useCallback, useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"

import { cancelTask } from "@/api/tasks"
import { ApprovalRequestCard } from "@/components/chat/approval-request-card"
import { AssistantMessage } from "@/components/chat/assistant-message"
import { ChatComposer } from "@/components/chat/chat-composer"
import { ChatEmptyState } from "@/components/chat/chat-empty-state"
import { ReasoningBlock } from "@/components/chat/reasoning-block"
import { TaskErrorCard } from "@/components/chat/task-error-card"
import { TaskNotificationMessage } from "@/components/chat/task-notification-message"
import { TaskProgressCard } from "@/components/chat/task-progress-card"
import {
  ToolCallCard,
  type ToolCallStatus,
} from "@/components/chat/tool-call-card"
import { TypingIndicator } from "@/components/chat/typing-indicator"
import { UserMessage } from "@/components/chat/user-message"
import { useApproval } from "@/hooks/use-approval"
import { useJavaClawChat } from "@/hooks/use-chat"
import { useTaskNotifications } from "@/hooks/use-task-notifications"
import { activeConversationIdAtom } from "@/store/chat"

interface ToolInvocationLike {
  toolCallId: string
  toolName: string
  state: "partial-call" | "call" | "result"
  args?: unknown
  result?: unknown
}

interface TaskNotificationPart {
  type: "task-notification"
  taskId: string
  taskName: string
  status: "completed" | "failed" | "cancelled" | "in_progress" | "awaiting_input" | "todo"
  message?: string
  durationMs?: number
}

interface ApprovalRequestPart {
  type: "approval-request"
  approvalId: string
  taskName: string
  question: string
  timeoutAt: string
  resolved?: "approved" | "denied" | "timeout" | null
}

/** Wrapper component that wires useApproval hook to ApprovalRequestCard. */
function ApprovalRequestCardWrapper({ part }: { part: ApprovalRequestPart }) {
  const approval = useApproval({
    id: part.approvalId,
    taskId: "",
    conversationId: "",
    question: part.question,
    status: part.resolved ?? "pending",
    timeoutAt: part.timeoutAt,
    createdAt: new Date().toISOString(),
  })

  return (
    <ApprovalRequestCard
      approvalId={part.approvalId}
      taskName={part.taskName}
      question={part.question}
      timeoutAt={part.timeoutAt}
      resolved={part.resolved ?? approval.resolved}
      isSubmitting={approval.isSubmitting}
      onApprove={approval.approve}
      onDeny={approval.deny}
      onRespond={approval.respond}
    />
  )
}

function toolStatus(state: ToolInvocationLike["state"]): ToolCallStatus {
  switch (state) {
    case "partial-call":
      return "pending"
    case "call":
      return "running"
    case "result":
      return "complete"
  }
}

type AssistantStatus = "ok" | "info" | "warn" | "error" | undefined

type ChatStatus = "submitted" | "streaming" | "ready" | "error"

/**
 * Derive a per-message status. The Vercel AI SDK exposes `status` ∈
 * {"submitted","streaming","ready","error"} at the CHAT hook level — not
 * per-message. Per-message signals come from two sources:
 *
 *   1. Chat-level status, mapped only onto the LAST assistant message:
 *      streaming/submitted → "info" (being written)
 *      error               → "error" (chat failed on this message)
 *
 *   2. Tool invocations inside the message's parts. If any completed tool
 *      reported an error (`result.isError === true`), the message is flagged
 *      "warn" — the text completed, but downstream work failed.
 *
 * Completed, trouble-free assistant messages intentionally return `undefined`
 * (no pill) to avoid per-message "done" badges cluttering the scrollback.
 */
function deriveAssistantStatus(
  message: Message,
  chatStatus: ChatStatus,
  isLast: boolean,
): AssistantStatus {
  if (isLast) {
    if (chatStatus === "streaming" || chatStatus === "submitted") return "info"
    if (chatStatus === "error") return "error"
  }

  const parts = (message.parts ?? []) as Array<{
    type: string
    toolInvocation?: ToolInvocationLike
  }>

  let hasToolError = false
  let sawCompletedTool = false
  for (const part of parts) {
    if (part.type !== "tool-invocation" || !part.toolInvocation) continue
    const inv = part.toolInvocation
    if (inv.state !== "result") continue
    sawCompletedTool = true
    const result = inv.result
    if (
      result !== null &&
      typeof result === "object" &&
      "isError" in result &&
      (result as { isError?: unknown }).isError === true
    ) {
      hasToolError = true
      break
    }
  }
  if (hasToolError) return "warn"
  // "ok" only on the LAST completed turn that actually ran tools. This makes
  // the badge a terminal confirmation of "done", not per-message noise across
  // the scrollback. Historical messages display without a pill.
  if (sawCompletedTool && isLast && chatStatus === "ready") return "ok"
  return undefined
}

function renderAssistantParts(
  message: Message,
  status: AssistantStatus,
  isStreaming: boolean,
  onCancelTask?: (taskId: string) => void,
) {
  // Concatenate text parts but interleave tool invocations at their position.
  const nodes: React.ReactNode[] = []
  let textBuffer = ""
  let textKey = 0
  const ts = message.createdAt

  const flushText = () => {
    if (textBuffer.trim().length > 0) {
      // Only the LAST text buffer inside the message gets streaming dots —
      // earlier text parts interleaved with tools are already complete.
      const willHaveMoreParts = false // tracked below via flushText call site
      nodes.push(
        <AssistantMessage
          key={`t-${textKey++}`}
          content={textBuffer}
          timestamp={ts}
          status={status}
          isStreaming={isStreaming && !willHaveMoreParts}
        />,
      )
    }
    textBuffer = ""
  }

  const parts = (message.parts ?? []) as Array<{
    type: string
    text?: string
    reasoning?: string
    toolInvocation?: ToolInvocationLike
    // Task-specific part fields (flattened union)
    taskId?: string
    taskName?: string
    status?: string
    message?: string
    durationMs?: number
    errorMessage?: string
    errorTrace?: string
    llmRequest?: string
    progressText?: string
    progressPercent?: number
    approvalId?: string
    question?: string
    timeoutAt?: string
    resolved?: "approved" | "denied" | "timeout" | null
  }>

  if (parts.length === 0 && message.content) {
    return [
      <AssistantMessage
        key="t-0"
        content={message.content}
        timestamp={ts}
        status={status}
        isStreaming={isStreaming}
      />,
    ]
  }

  // Detect whether there's any text part after a reasoning part — used to know
  // whether reasoning is "done" (text started arriving means reasoning ended).
  let reasoningIsStreaming = isStreaming
  const firstTextIdx = parts.findIndex(
    (p) => p.type === "text" && (p.text ?? "").trim().length > 0,
  )
  if (firstTextIdx >= 0) reasoningIsStreaming = false

  let reasoningKey = 0
  for (const part of parts) {
    if (part.type === "reasoning") {
      const rText =
        typeof part.reasoning === "string"
          ? part.reasoning
          : typeof part.text === "string"
            ? part.text
            : ""
      flushText()
      nodes.push(
        <ReasoningBlock
          key={`reasoning-${reasoningKey++}`}
          content={rText}
          isStreaming={reasoningIsStreaming}
        />,
      )
    } else if (part.type === "text" && typeof part.text === "string") {
      textBuffer += part.text
    } else if (part.type === "tool-invocation" && part.toolInvocation) {
      flushText()
      const inv = part.toolInvocation
      nodes.push(
        <ToolCallCard
          key={`tool-${inv.toolCallId}`}
          name={inv.toolName}
          status={toolStatus(inv.state)}
          input={inv.args}
          output={inv.state === "result" ? inv.result : undefined}
        />,
      )
    } else if (part.type === "task-notification" && part.taskId) {
      flushText()
      nodes.push(
        <TaskNotificationMessage
          key={`task-notif-${part.taskId}`}
          taskId={part.taskId}
          taskName={part.taskName ?? "Task"}
          status={(part.status as TaskNotificationPart["status"]) ?? "todo"}
          message={part.message}
          durationMs={part.durationMs}
          timestamp={ts}
        />,
      )
    } else if (part.type === "task-error" && part.taskId) {
      flushText()
      nodes.push(
        <TaskErrorCard
          key={`task-err-${part.taskId}`}
          taskId={part.taskId}
          taskName={part.taskName ?? "Task"}
          errorMessage={part.errorMessage ?? "Unknown error"}
          errorTrace={part.errorTrace}
          llmRequest={part.llmRequest}
          durationMs={part.durationMs}
        />,
      )
    } else if (part.type === "task-progress" && part.taskId) {
      flushText()
      nodes.push(
        <TaskProgressCard
          key={`task-prog-${part.taskId}`}
          taskId={part.taskId}
          taskName={part.taskName ?? "Task"}
          progressText={part.progressText}
          progressPercent={part.progressPercent}
          onCancel={onCancelTask}
        />,
      )
    } else if (part.type === "approval-request" && part.approvalId) {
      flushText()
      nodes.push(
        <ApprovalRequestCardWrapper
          key={`approval-${part.approvalId}`}
          part={part as unknown as ApprovalRequestPart}
        />,
      )
    }
  }
  flushText()
  return nodes
}

export function ChatPage() {
  const { t } = useTranslation()
  const { messages, input, handleInputChange, handleSubmit, status, stop } =
    useJavaClawChat()
  const conversationId = useAtomValue(activeConversationIdAtom)
  const scrollRef = useRef<HTMLDivElement>(null)
  const [autoScroll, setAutoScroll] = useState(true)

  // SSE subscription for real-time task notifications
  useTaskNotifications(conversationId)

  const handleCancelTask = useCallback(
    async (taskId: string) => {
      try {
        await cancelTask(taskId)
      } catch {
        // cancel failure is non-critical
      }
    },
    [],
  )

  const isStreaming = status === "submitted" || status === "streaming"

  // Show the typing indicator (witty phrases) until the assistant has actually
  // produced visible content — not just until its message slot is created.
  // This eliminates the empty-screen gap between TypingIndicator disappearing
  // and the first streamed token rendering.
  const lastMessage = messages.length > 0 ? messages[messages.length - 1] : null
  const lastAssistantHasContent = (() => {
    if (!lastMessage || lastMessage.role !== "assistant") return false
    if ((lastMessage.content ?? "").trim().length > 0) return true
    const parts = (lastMessage.parts ?? []) as Array<{
      type: string
      text?: string
    }>
    return parts.some(
      (p) => p.type === "text" && (p.text ?? "").trim().length > 0,
    )
  })()
  const isTyping =
    status === "submitted" ||
    (status === "streaming" && !lastAssistantHasContent)

  useEffect(() => {
    if (!autoScroll || !scrollRef.current) return
    scrollRef.current.scrollTop = scrollRef.current.scrollHeight
  }, [messages, autoScroll, status])

  const onScroll = (event: React.UIEvent<HTMLDivElement>) => {
    const el = event.currentTarget
    const nearBottom =
      el.scrollHeight - el.scrollTop - el.clientHeight < 48
    setAutoScroll(nearBottom)
  }

  const triggerSend = () => {
    handleSubmit(new Event("submit"))
  }

  const handleChange = (value: string) => {
    handleInputChange({
      target: { value },
    } as React.ChangeEvent<HTMLTextAreaElement>)
  }

  return (
    <div
      className="flex h-full min-h-0 flex-col bg-background"
      aria-label={t("chat.title")}
    >
      <div
        ref={scrollRef}
        onScroll={onScroll}
        className="min-h-0 flex-1 overflow-y-auto px-4 py-6 md:px-11"
      >
        <div className="mx-auto flex w-full max-w-[760px] flex-col gap-5 pb-4">
          {messages.length === 0 && !isTyping ? (
            <ChatEmptyState
              onSuggestion={(prompt) => {
                handleChange(prompt)
              }}
            />
          ) : (
            messages.map((message, idx) => {
              const isLast = idx === messages.length - 1
              return (
                <div key={message.id} className="flex w-full">
                  {message.role === "assistant" ? (
                    <div className="flex w-full flex-col gap-2">
                      {renderAssistantParts(
                        message,
                        deriveAssistantStatus(message, status, isLast),
                        isLast && isStreaming,
                        handleCancelTask,
                      )}
                    </div>
                  ) : message.role === "user" ? (
                    <UserMessage content={message.content} />
                  ) : null}
                </div>
              )
            })
          )}
          {isTyping && <TypingIndicator />}
        </div>
      </div>
      <ChatComposer
        value={input}
        onChange={handleChange}
        onSend={triggerSend}
        onStop={stop}
        isStreaming={isStreaming}
      />
    </div>
  )
}

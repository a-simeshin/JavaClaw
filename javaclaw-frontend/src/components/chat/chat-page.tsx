import type { Message } from "@ai-sdk/react"
import { useEffect, useRef, useState } from "react"
import { useTranslation } from "react-i18next"

import { AssistantMessage } from "@/components/chat/assistant-message"
import { ChatComposer } from "@/components/chat/chat-composer"
import { ChatEmptyState } from "@/components/chat/chat-empty-state"
import { ReasoningBlock } from "@/components/chat/reasoning-block"
import {
  ToolCallCard,
  type ToolCallStatus,
} from "@/components/chat/tool-call-card"
import { TypingIndicator } from "@/components/chat/typing-indicator"
import { UserMessage } from "@/components/chat/user-message"
import { useJavaClawChat } from "@/hooks/use-chat"

interface ToolInvocationLike {
  toolCallId: string
  toolName: string
  state: "partial-call" | "call" | "result"
  args?: unknown
  result?: unknown
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
    }
  }
  flushText()
  return nodes
}

export function ChatPage() {
  const { t } = useTranslation()
  const { messages, input, handleInputChange, handleSubmit, status, stop } =
    useJavaClawChat()
  const scrollRef = useRef<HTMLDivElement>(null)
  const [autoScroll, setAutoScroll] = useState(true)

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

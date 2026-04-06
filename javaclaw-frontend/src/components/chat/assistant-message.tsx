import { IconCheck, IconCopy } from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"

import { cn } from "@/lib/utils"

type AssistantStatus = "ok" | "info" | "warn" | "error"

interface AssistantMessageProps {
  content: string
  status?: AssistantStatus
  /** `Date` from ai-sdk message, or a pre-formatted string. Renders as HH:MM. */
  timestamp?: Date | string
  /** When true, appends 3 pulsing accent dots after content (live stream). */
  isStreaming?: boolean
  className?: string
}

function formatTimestamp(value: Date | string | undefined): string | undefined {
  if (value === undefined) return undefined
  if (typeof value === "string") return value
  if (!(value instanceof Date) || Number.isNaN(value.getTime())) return undefined
  return value.toLocaleTimeString(undefined, {
    hour: "2-digit",
    minute: "2-digit",
    hour12: false,
  })
}

/**
 * Normalise quirky model outputs so react-markdown renders them correctly:
 *  - Single-line triple-backtick blocks (```java foo() ```) — some models
 *    emit code fences without surrounding newlines. We split them into
 *    proper multi-line blocks.
 */
function normaliseMarkdown(md: string): string {
  // ```lang? <code> ```  on one logical chunk (no inner newlines in body)
  return md.replace(
    /```([a-zA-Z0-9_+-]*)[ \t]+([^\n`][^\n]*?)[ \t]*```/g,
    (_match, lang: string, code: string) => {
      const langTag = lang ?? ""
      return `\n\`\`\`${langTag}\n${code.trim()}\n\`\`\`\n`
    },
  )
}

/**
 * Escalate outer code-fence length when its content contains another fence-
 * looking line (e.g. ```python inside ```md). CommonMark closes the outer
 * block at the first subsequent ``` on its own line, so a model that writes
 * a nested code example using three backticks both inside and outside gets
 * rendered as "open, close on first inner fence, orphan content, re-open…".
 *
 * Heuristic: when we see an opener followed later by a line that starts with
 * 3+ backticks (opener-shaped: with info text, or content-shaped), treat the
 * LAST ``` run on its own line as the real closer and widen the outer fence
 * to `max(inner-run)+1` backticks so it uniquely brackets the whole region.
 * When no nested-fence-looking line is present, behaviour matches the parser
 * (first valid closer wins), so normal two-block documents are untouched.
 */
function escalateNestedFences(md: string): string {
  const lines = md.split("\n")
  const out: string[] = []
  let i = 0
  while (i < lines.length) {
    const line = lines[i]
    // Opener: ≤3 leading spaces, ≥3 backticks, optional info text (no backticks).
    const openMatch = line.match(/^( {0,3})(`{3,})([^`]*)$/)
    if (!openMatch) {
      out.push(line)
      i++
      continue
    }
    const indent = openMatch[1]
    const openFence = openMatch[2]
    const info = openMatch[3]

    // Scan forward: locate first + last valid closers, detect nested-fence lines.
    let firstCloseIdx = -1
    let lastCloseIdx = -1
    let hasNestedFenceLine = false
    for (let j = i + 1; j < lines.length; j++) {
      const cl = lines[j]
      const closer = cl.match(/^ {0,3}(`{3,})\s*$/)
      const nestedOpener = cl.match(/^ {0,3}`{3,}[^`\s]/) // fence with info text
      if (closer && closer[1].length >= openFence.length) {
        if (firstCloseIdx === -1) firstCloseIdx = j
        lastCloseIdx = j
      } else if (nestedOpener) {
        hasNestedFenceLine = true
      }
    }

    if (firstCloseIdx === -1) {
      // Unclosed block — leave it to stabilizeStreamingMarkdown / react-markdown.
      out.push(line)
      i++
      continue
    }

    const closeIdx = hasNestedFenceLine ? lastCloseIdx : firstCloseIdx

    // Find the longest ``` run inside the content so we escalate enough.
    let maxInnerRun = 0
    for (let j = i + 1; j < closeIdx; j++) {
      const runs = lines[j].match(/`{3,}/g)
      if (runs) {
        for (const r of runs) {
          if (r.length > maxInnerRun) maxInnerRun = r.length
        }
      }
    }
    const needed = Math.max(openFence.length, maxInnerRun + 1)
    const newFence = "`".repeat(needed)

    out.push(indent + newFence + info)
    for (let k = i + 1; k < closeIdx; k++) out.push(lines[k])
    out.push(newFence)
    i = closeIdx + 1
  }
  return out.join("\n")
}

/**
 * Stabilise markdown mid-stream so incomplete syntax doesn't hijack rendering:
 *  - Unclosed ``` fences would swallow the rest of the message as a code block.
 *  - Unclosed single backticks would swallow the current line as inline code.
 *  - Unclosed `**` / `__` would render nothing until closed.
 * We append closing tokens speculatively; the "real" close from the stream
 * will simply replace ours on the next render.
 */
function stabilizeStreamingMarkdown(md: string): string {
  let result = md
  const fenceCount = (result.match(/```/g) ?? []).length
  if (fenceCount % 2 === 1) {
    result = result + "\n```"
  }
  // Count inline backticks outside of now-balanced fences.
  const withoutFences = result.replace(/```[\s\S]*?```/g, "")
  const inlineTicks = (withoutFences.match(/`/g) ?? []).length
  if (inlineTicks % 2 === 1) {
    result = result + "`"
  }
  // Bold: ** runs
  const boldRuns = (result.match(/\*\*/g) ?? []).length
  if (boldRuns % 2 === 1) {
    result = result + "**"
  }
  return result
}

/**
 * Assistant message per design ctx v3 §10 + anti-AI rules:
 *  - No card, no outer border. Thin 2px left-border color-coded by status.
 *  - Header: mono agent name + timestamp + optional status pill.
 *  - Body: Outfit 15px/1.65, prose markdown, code in JetBrains Mono.
 */
export function AssistantMessage({
  content,
  status,
  timestamp,
  isStreaming = false,
  className,
}: AssistantMessageProps) {
  const { t } = useTranslation()
  const [copied, setCopied] = useState(false)
  const formattedTs = formatTimestamp(timestamp)
  const renderedContent = escalateNestedFences(
    normaliseMarkdown(isStreaming ? stabilizeStreamingMarkdown(content) : content),
  )

  // Don't render an empty shell — the TypingIndicator keeps the user informed
  // until the first token actually arrives.
  if (!content.trim()) return null

  const handleCopy = async () => {
    if (!content) return
    try {
      await navigator.clipboard.writeText(content)
      setCopied(true)
      setTimeout(() => setCopied(false), 1600)
    } catch {
      // ignore clipboard errors
    }
  }

  return (
    <div
      data-role="assistant"
      data-status={status}
      className={cn(
        "group relative w-full animate-[jc-fade-in_0.25s_cubic-bezier(.25,.8,.25,1)_both] border-l-2 py-3.5 pl-[18px]",
        "border-separator",
        status === "ok" && "border-l-success",
        status === "info" && "border-l-info",
        status === "warn" && "border-l-warning-text",
        status === "error" && "border-l-error",
        className,
      )}
    >
      <div className="mb-2 flex items-center gap-2">
        <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.05em] text-muted-foreground">
          {t("chat.assistant")}
        </span>
        {formattedTs && (
          <span className="font-mono text-[11px] tracking-[0.02em] text-muted-foreground tabular-nums">
            {formattedTs}
          </span>
        )}
        {status && (
          <span
            className={cn(
              "font-mono text-[10px] font-bold uppercase tracking-[0.04em] rounded-full px-2 py-[2px]",
              status === "ok" && "bg-success/10 text-success",
              status === "info" && "bg-info/10 text-info",
              status === "warn" && "bg-warning-text/10 text-warning-text",
              status === "error" && "bg-error/10 text-error",
            )}
          >
            {STATUS_LABEL[status]}
          </span>
        )}
        <button
          type="button"
          onClick={handleCopy}
          aria-label={copied ? t("actions.copied") : t("actions.copy")}
          className={cn(
            "ml-auto inline-flex h-6 items-center gap-1 rounded-sm px-1.5 font-mono text-[11px] text-muted-foreground opacity-0 transition-opacity hover:bg-surface-hover hover:text-foreground group-hover:opacity-100 focus-visible:opacity-100",
          )}
        >
          {copied ? (
            <IconCheck width={12} height={12} strokeWidth={1.5} aria-hidden />
          ) : (
            <IconCopy width={12} height={12} strokeWidth={1.5} aria-hidden />
          )}
          <span>{copied ? t("actions.copied") : t("actions.copy")}</span>
        </button>
      </div>
      <div
        className={cn(
          "prose prose-sm dark:prose-invert max-w-none text-[15px] leading-[1.65] text-foreground [overflow-wrap:anywhere]",
          "prose-p:my-2 prose-p:text-foreground",
          "prose-headings:font-mono prose-headings:font-bold prose-headings:text-foreground prose-headings:tracking-[-0.02em]",
          "prose-strong:text-foreground prose-strong:font-semibold",
          "prose-a:text-accent-strong prose-a:font-medium prose-a:no-underline hover:prose-a:underline",
          "prose-pre:my-3 prose-pre:overflow-x-auto prose-pre:rounded-sm prose-pre:border prose-pre:border-surface-code-border prose-pre:bg-surface-code prose-pre:p-3 prose-pre:font-mono prose-pre:text-[13px] prose-pre:leading-[1.55]",
          "prose-code:rounded prose-code:border prose-code:border-surface-code-border prose-code:bg-surface-code prose-code:px-1.5 prose-code:py-0.5 prose-code:font-mono prose-code:text-[13px] prose-code:text-accent-strong prose-code:font-medium prose-code:before:content-none prose-code:after:content-none",
          "prose-blockquote:border-l-2 prose-blockquote:border-l-accent-strong prose-blockquote:bg-accent-bg prose-blockquote:py-1 prose-blockquote:pl-4 prose-blockquote:pr-2 prose-blockquote:not-italic prose-blockquote:text-foreground prose-blockquote:[&>p]:before:content-none prose-blockquote:[&>p]:after:content-none",
          "prose-ul:my-2 prose-ol:my-2 prose-li:my-0.5 prose-li:text-foreground prose-li:marker:text-accent-strong",
          "prose-hr:border-separator prose-hr:my-4",
          "prose-table:my-3 prose-table:font-mono prose-table:text-[12px] prose-table:border prose-table:border-border prose-table:overflow-hidden prose-table:rounded-sm",
          "prose-th:border-b prose-th:border-border prose-th:bg-surface-elevated prose-th:px-3.5 prose-th:py-1.5 prose-th:text-left prose-th:text-[9px] prose-th:font-bold prose-th:uppercase prose-th:tracking-[0.07em] prose-th:text-muted-foreground",
          "prose-td:border-b prose-td:border-border prose-td:px-3.5 prose-td:py-1.5 prose-td:text-foreground",
        )}
      >
        <ReactMarkdown remarkPlugins={[remarkGfm]}>{renderedContent}</ReactMarkdown>
        {isStreaming && (
          <span
            aria-hidden
            className="ml-1 inline-flex translate-y-[-2px] gap-[3px] align-middle"
          >
            <span className="h-1 w-1 rounded-full bg-accent-strong animate-[jc-streaming-dot_1.4s_ease_infinite]" />
            <span className="h-1 w-1 rounded-full bg-accent-strong animate-[jc-streaming-dot_1.4s_ease_infinite] [animation-delay:150ms]" />
            <span className="h-1 w-1 rounded-full bg-accent-strong animate-[jc-streaming-dot_1.4s_ease_infinite] [animation-delay:300ms]" />
          </span>
        )}
      </div>
    </div>
  )
}

const STATUS_LABEL: Record<AssistantStatus, string> = {
  ok: "done",
  info: "info",
  warn: "warn",
  error: "fail",
}

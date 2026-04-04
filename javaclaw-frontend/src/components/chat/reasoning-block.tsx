import { IconBrain, IconChevronRight } from "@tabler/icons-react"
import { useState } from "react"
import { useTranslation } from "react-i18next"

import { cn } from "@/lib/utils"

interface ReasoningBlockProps {
  content: string
  /** When true, block is auto-expanded and shows a subtle pulse. */
  isStreaming?: boolean
  className?: string
}

/**
 * Reasoning / chain-of-thought block, in the spirit of DeepSeek R1 and
 * Claude extended-thinking displays. Surfaces the model's internal thinking
 * inside a collapsible panel so it doesn't compete visually with the real
 * answer. Auto-expands while streaming so users see progress, collapses when
 * the reasoning stream completes (user can always re-open).
 */
export function ReasoningBlock({
  content,
  isStreaming = false,
  className,
}: ReasoningBlockProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(isStreaming)

  // Keep expanded while streaming; auto-collapse on first idle render after.
  const isOpen = isStreaming ? true : open

  if (!content.trim() && !isStreaming) return null

  return (
    <div
      className={cn(
        "relative flex w-full flex-col overflow-hidden rounded-sm border border-border bg-surface-raised",
        isStreaming &&
          "animate-[jc-pulse-subtle_1.8s_ease-in-out_infinite]",
        className,
      )}
    >
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        aria-expanded={isOpen}
        className="flex items-center gap-2.5 px-3.5 py-2 text-left transition-colors hover:bg-surface-hover/20"
      >
        <IconChevronRight
          width={14}
          height={14}
          strokeWidth={1.5}
          aria-hidden
          className={cn(
            "shrink-0 text-muted-foreground transition-transform duration-150",
            isOpen && "rotate-90",
          )}
        />
        <IconBrain
          width={14}
          height={14}
          strokeWidth={1.5}
          aria-hidden
          className="shrink-0 text-accent-strong"
        />
        <span className="flex-1 truncate font-mono text-[11px] font-semibold uppercase tracking-[0.05em] text-muted-foreground">
          {isStreaming
            ? t("chat.reasoning.thinking", { defaultValue: "Thinking…" })
            : t("chat.reasoning.label", { defaultValue: "Reasoning" })}
        </span>
      </button>
      {isOpen && content.trim() && (
        <div className="border-t border-border px-3.5 py-3">
          <pre className="whitespace-pre-wrap break-words font-mono text-[12px] leading-[1.55] text-muted-foreground">
            {content}
          </pre>
        </div>
      )}
    </div>
  )
}

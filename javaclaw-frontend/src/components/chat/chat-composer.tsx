import { IconArrowUp, IconPlayerStopFilled } from "@tabler/icons-react"
import type { KeyboardEvent } from "react"
import { useTranslation } from "react-i18next"
import TextareaAutosize from "react-textarea-autosize"

import { cn } from "@/lib/utils"

interface ChatComposerProps {
  value: string
  onChange: (value: string) => void
  onSend: () => void
  onStop?: () => void
  isStreaming?: boolean
  disabled?: boolean
  className?: string
}

/**
 * Composer per design ctx v3 §6.3 + §10:
 * pill-shaped search-bar style, 24px radius, separator border, accent focus-ring
 * with soft glow. Send button fills primary when content is ready.
 */
export function ChatComposer({
  value,
  onChange,
  onSend,
  onStop,
  isStreaming = false,
  disabled = false,
  className,
}: ChatComposerProps) {
  const { t } = useTranslation()
  const canSubmit = !disabled && value.trim().length > 0 && !isStreaming

  const handleKeyDown = (event: KeyboardEvent<HTMLTextAreaElement>) => {
    if (event.nativeEvent.isComposing) return
    const isSubmit =
      event.key === "Enter" && (event.metaKey || event.ctrlKey || !event.shiftKey)
    if (event.key === "Enter" && event.shiftKey) return
    if (isSubmit) {
      event.preventDefault()
      if (isStreaming) {
        onStop?.()
        return
      }
      if (canSubmit) onSend()
    }
  }

  return (
    <div
      className={cn(
        "border-t border-border bg-background px-4 pt-4 pb-6 md:px-11",
        className,
      )}
    >
      <form
        onSubmit={(event) => {
          event.preventDefault()
          if (isStreaming) {
            onStop?.()
            return
          }
          if (canSubmit) onSend()
        }}
        className={cn(
          "mx-auto flex w-full max-w-[760px] items-center gap-2.5 rounded-pill border border-separator bg-secondary px-[18px] py-3 transition-all duration-200",
          "focus-within:border-ring focus-within:shadow-[0_0_0_3px_var(--accent-bg)]",
        )}
      >
        <TextareaAutosize
          value={value}
          onChange={(event) => onChange(event.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={t("chat.placeholder")}
          disabled={disabled}
          minRows={1}
          maxRows={10}
          aria-label={t("chat.placeholder")}
          className={cn(
            "flex-1 resize-none bg-transparent text-[15px] leading-[1.5] text-foreground outline-none placeholder:text-muted-foreground disabled:cursor-not-allowed disabled:opacity-60",
          )}
        />
        {isStreaming ? (
          <button
            type="button"
            onClick={onStop}
            aria-label={t("chat.abort")}
            className={cn(
              "inline-flex h-[30px] shrink-0 items-center gap-1.5 rounded-3xl border border-separator bg-card px-[14px] font-sans text-[13px] font-medium text-foreground transition-colors duration-150 hover:bg-surface-elevated",
            )}
          >
            <IconPlayerStopFilled width={12} height={12} aria-hidden />
            <span>{t("chat.abort")}</span>
          </button>
        ) : (
          <button
            type="submit"
            disabled={!canSubmit}
            aria-label={t("actions.send")}
            data-ready={canSubmit || undefined}
            className={cn(
              "inline-flex h-[30px] w-[30px] shrink-0 items-center justify-center rounded-3xl font-sans transition-all duration-150",
              "bg-card text-foreground/70 hover:bg-surface-elevated hover:text-foreground",
              "data-[ready]:bg-primary data-[ready]:text-primary-foreground data-[ready]:hover:bg-surface-hover",
              "disabled:cursor-not-allowed disabled:opacity-40",
            )}
          >
            <IconArrowUp width={14} height={14} strokeWidth={1.75} aria-hidden />
          </button>
        )}
      </form>
    </div>
  )
}

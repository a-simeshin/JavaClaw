import { useEffect, useMemo, useRef, useState } from "react"
import { useTranslation } from "react-i18next"

import { pickWittyPhrases } from "@/components/chat/witty-phrases"
import { cn } from "@/lib/utils"

interface TypingIndicatorProps {
  className?: string
}

const PHRASE_INTERVAL_MS = 4000

/**
 * Typing indicator shown while waiting for the assistant's first token.
 * Displays a cycling witty phrase (ported from qwen-code) + pulsing accent
 * dots. Matches AssistantMessage composition: 2px left-border, mono label.
 */
export function TypingIndicator({ className }: TypingIndicatorProps) {
  const { t, i18n } = useTranslation()
  const phrases = useMemo(
    () => pickWittyPhrases(i18n.resolvedLanguage ?? "en"),
    [i18n.resolvedLanguage],
  )
  const [index, setIndex] = useState(
    () => Math.floor(Math.random() * phrases.length),
  )
  const phraseRef = useRef(phrases)
  phraseRef.current = phrases

  useEffect(() => {
    const id = window.setInterval(() => {
      const list = phraseRef.current
      setIndex(Math.floor(Math.random() * list.length))
    }, PHRASE_INTERVAL_MS)
    return () => window.clearInterval(id)
  }, [])

  return (
    <div
      role="status"
      aria-live="polite"
      aria-label={t("chat.typing")}
      className={cn(
        "w-full border-l-2 border-l-info py-3.5 pl-[18px]",
        className,
      )}
    >
      <div className="mb-2 flex items-center gap-2">
        <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.05em] text-muted-foreground">
          {t("chat.assistant")}
        </span>
      </div>
      <div className="flex items-center gap-2 text-[14px] font-normal text-muted-foreground">
        <span key={index} className="animate-[jc-fade-in_0.3s_ease-out_both]">
          {phrases[index]}
        </span>
        <span aria-hidden className="inline-flex gap-[3px]">
          <span className="h-1 w-1 rounded-full bg-accent-strong animate-[jc-streaming-dot_1.4s_ease_infinite]" />
          <span className="h-1 w-1 rounded-full bg-accent-strong animate-[jc-streaming-dot_1.4s_ease_infinite] [animation-delay:150ms]" />
          <span className="h-1 w-1 rounded-full bg-accent-strong animate-[jc-streaming-dot_1.4s_ease_infinite] [animation-delay:300ms]" />
        </span>
      </div>
    </div>
  )
}

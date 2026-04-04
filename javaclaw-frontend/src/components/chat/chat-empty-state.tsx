import { IconMessage } from "@tabler/icons-react"
import { useTranslation } from "react-i18next"

interface ChatEmptyStateProps {
  onSuggestion?: (prompt: string) => void
}

/**
 * Empty state per design ctx v3 §10.4: icon + heading + **action**.
 * Offers a short list of concrete suggestion-prompts so new users have a
 * discoverable first-move instead of staring at a blank chat.
 */
export function ChatEmptyState({ onSuggestion }: ChatEmptyStateProps) {
  const { t } = useTranslation()

  const suggestions = [
    t("chat.empty.suggest.status", {
      defaultValue: "Show backend health and recent activity",
    }),
    t("chat.empty.suggest.skills", {
      defaultValue: "List available skills and MCP servers",
    }),
    t("chat.empty.suggest.logs", {
      defaultValue: "Tail the last 20 error log entries",
    }),
  ]

  return (
    <div className="flex h-full flex-col items-center justify-center gap-6 px-6 py-16">
      <IconMessage
        width={36}
        height={36}
        strokeWidth={1.5}
        aria-hidden
        className="text-accent-strong"
      />
      <div className="flex max-w-md flex-col items-center gap-2 text-center">
        <h2 className="font-mono text-[20px] font-bold leading-[1.2] tracking-[-0.02em] text-foreground">
          {t("chat.empty.title")}
        </h2>
        <p className="text-[15px] leading-[1.5] text-muted-foreground">
          {t("chat.empty.description")}
        </p>
      </div>
      {onSuggestion && (
        <ul className="flex w-full max-w-[560px] flex-col gap-2">
          {suggestions.map((prompt) => (
            <li key={prompt}>
              <button
                type="button"
                onClick={() => onSuggestion(prompt)}
                className="group flex w-full items-center gap-3 rounded-sm border border-separator bg-secondary px-4 py-2.5 text-left text-[14px] leading-[1.5] text-foreground transition-colors duration-150 hover:border-accent-strong hover:bg-surface-hover"
              >
                <span
                  aria-hidden
                  className="font-mono text-[14px] font-bold leading-none text-muted-foreground group-hover:text-accent-strong"
                >
                  ›
                </span>
                <span className="flex-1 truncate">{prompt}</span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  )
}

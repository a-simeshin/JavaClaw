import { IconPlus, IconSearch, IconTrash } from "@tabler/icons-react"
import { useAtom } from "jotai"
import { useMemo, useState } from "react"
import { useTranslation } from "react-i18next"

import type { ConversationDto } from "@/api/conversations"
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
import { useConversationHistory } from "@/hooks/use-conversation-history"
import { cn } from "@/lib/utils"
import { activeConversationIdAtom } from "@/store/chat"
import { conversationSearchAtom } from "@/store/conversations"

interface ConversationHistoryMenuProps {
  className?: string
}

function formatRelative(dateIso: string | undefined): string {
  if (!dateIso) return ""
  const date = new Date(dateIso)
  if (Number.isNaN(date.getTime())) return ""
  // Guard against epoch-0 / unset timestamps coming from the backend.
  if (date.getFullYear() < 2000) return ""
  const diffMs = Date.now() - date.getTime()
  const min = Math.floor(diffMs / 60000)
  if (min < 1) return "now"
  if (min < 60) return `${min}m`
  const hr = Math.floor(min / 60)
  if (hr < 24) return `${hr}h`
  const day = Math.floor(hr / 24)
  if (day < 7) return `${day}d`
  return date.toLocaleDateString()
}

export function ConversationHistoryMenu({
  className,
}: ConversationHistoryMenuProps) {
  const { t } = useTranslation()
  const [activeId, setActiveId] = useAtom(activeConversationIdAtom)
  const [search, setSearch] = useAtom(conversationSearchAtom)
  const [pendingDelete, setPendingDelete] = useState<ConversationDto | null>(
    null,
  )

  const {
    conversations,
    isLoading,
    isError,
    deleteConversation,
  } = useConversationHistory()

  const filtered = useMemo(() => {
    const q = search.trim().toLowerCase()
    if (!q) return conversations
    return conversations.filter((c) =>
      (c.title ?? "").toLowerCase().includes(q),
    )
  }, [conversations, search])

  const handleNew = () => {
    setActiveId(null)
  }

  const handleConfirmDelete = () => {
    if (!pendingDelete) return
    deleteConversation(pendingDelete.id)
    if (activeId === pendingDelete.id) {
      setActiveId(null)
    }
    setPendingDelete(null)
  }

  return (
    <div
      aria-label={t("conversations.title")}
      className={cn("flex min-h-0 flex-1 flex-col gap-2 px-3 py-3", className)}
    >
      <div className="flex items-center justify-between px-2">
        <span className="font-mono text-[11px] font-semibold uppercase tracking-[0.12em] text-muted-foreground">
          {t("conversations.title")}
        </span>
        <button
          type="button"
          onClick={handleNew}
          aria-label={t("actions.newConversation")}
          className={cn(
            "inline-flex h-6 w-6 items-center justify-center rounded-sm text-muted-foreground transition-colors",
            "hover:bg-surface-hover hover:text-foreground",
          )}
        >
          <IconPlus width={14} height={14} strokeWidth={1.5} aria-hidden />
        </button>
      </div>
      <div className="relative">
        <IconSearch
          width={14}
          height={14}
          strokeWidth={1.5}
          aria-hidden
          className="pointer-events-none absolute left-2.5 top-1/2 -translate-y-1/2 text-muted-foreground"
        />
        <input
          type="text"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder={t("conversations.searchPlaceholder")}
          aria-label={t("actions.search")}
          className={cn(
            "h-8 w-full rounded-sm border border-separator bg-secondary pl-8 pr-2 text-[13px] text-foreground placeholder:text-muted-foreground",
            "focus:outline-none transition-colors",
          )}
        />
      </div>
      <ul
        role="list"
        className="flex min-h-0 flex-1 flex-col gap-0.5 overflow-y-auto pr-1"
      >
        {isError && (
          <li className="px-2 py-1.5 text-[13px] text-error">
            {t("conversations.loadError")}
          </li>
        )}
        {!isError && filtered.length === 0 && !isLoading && (
          <li className="px-2 py-1.5 text-[13px] text-muted-foreground">
            {search.trim()
              ? t("conversations.noResults")
              : t("conversations.empty")}
          </li>
        )}
        {filtered.map((conv) => {
          const active = conv.id === activeId
          return (
            <li key={conv.id}>
              <div
                className={cn(
                  "group relative flex items-center gap-1 rounded-sm pr-1 transition-colors duration-150",
                  active
                    ? "bg-surface-selection"
                    : "hover:bg-surface-hover",
                )}
              >
                <button
                  type="button"
                  onClick={() => setActiveId(conv.id)}
                  aria-current={active ? "true" : undefined}
                  className={cn(
                    "flex min-w-0 flex-1 flex-col items-start gap-0 rounded-sm px-2.5 py-1.5 text-left",
                  )}
                  style={
                    active
                      ? {
                          boxShadow: "inset 2px 0 0 var(--accent-strong)",
                        }
                      : undefined
                  }
                >
                  <span className="line-clamp-2 text-[14px] leading-[1.35] text-foreground">
                    {conv.title || conv.id.slice(0, 8)}
                  </span>
                  <span className="mt-0.5 font-mono text-[11px] tracking-[0.02em] text-muted-foreground tabular-nums">
                    {formatRelative(conv.updatedAt)}
                  </span>
                </button>
                <button
                  type="button"
                  onClick={(event) => {
                    event.stopPropagation()
                    setPendingDelete(conv)
                  }}
                  aria-label={t("actions.delete")}
                  className={cn(
                    "inline-flex h-6 w-6 shrink-0 items-center justify-center rounded-sm text-muted-foreground opacity-0 transition-opacity",
                    "hover:bg-error/10 hover:text-error group-hover:opacity-100 focus-visible:opacity-100",
                  )}
                >
                  <IconTrash
                    width={12}
                    height={12}
                    strokeWidth={1.5}
                    aria-hidden
                  />
                </button>
              </div>
            </li>
          )
        })}
      </ul>
      <Dialog
        open={pendingDelete !== null}
        onOpenChange={(open) => {
          if (!open) setPendingDelete(null)
        }}
      >
        <DialogContent className="sm:max-w-sm">
          <DialogHeader>
            <DialogTitle>{t("conversations.deletePromptTitle")}</DialogTitle>
            <DialogDescription>
              {t("conversations.deletePromptDescription")}
            </DialogDescription>
          </DialogHeader>
          <DialogFooter>
            <button
              type="button"
              onClick={() => setPendingDelete(null)}
              className="inline-flex h-8 items-center justify-center rounded-sm border border-separator bg-secondary px-3 text-[13px] text-foreground transition-colors hover:bg-surface-hover"
            >
              {t("actions.cancel")}
            </button>
            <button
              type="button"
              onClick={handleConfirmDelete}
              className="inline-flex h-8 items-center justify-center rounded-sm bg-error/10 px-3 text-[13px] font-medium text-error transition-colors hover:bg-error/20"
            >
              {t("actions.delete")}
            </button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  )
}

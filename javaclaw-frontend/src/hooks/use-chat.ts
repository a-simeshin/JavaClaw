import type { Message } from "@ai-sdk/react"
import { useChat as useAiChat } from "@ai-sdk/react"
import { useQuery } from "@tanstack/react-query"
import { useAtom } from "jotai"
import { useEffect, useMemo, useRef } from "react"

import { CHAT_SEND_ENDPOINT, createChatFetch } from "@/api/chat"
import {
  listConversationMessages,
  type MessageDto,
} from "@/api/conversations"
import { activeConversationIdAtom, isStreamingAtom } from "@/store/chat"

interface UseJavaClawChatOptions {
  onUnauthorized?: () => void
}

function defaultOnUnauthorized() {
  if (typeof window !== "undefined" && window.location.pathname !== "/login") {
    window.location.href = "/login"
  }
}

/** Convert the backend {@link MessageDto} list to @ai-sdk/react {@link Message}s. */
function toAiMessages(dtos: MessageDto[]): Message[] {
  // Only user + assistant are rendered by ChatPage. System/tool rows from
  // chat-memory are dropped here so they don't leak into the transcript view.
  return dtos
    .filter((m) => m.role === "user" || m.role === "assistant")
    .map((m) => ({
      id: m.id,
      role: m.role as "user" | "assistant",
      content: m.content,
      createdAt: m.createdAt ? new Date(m.createdAt) : undefined,
    }))
}

export function useJavaClawChat(options: UseJavaClawChatOptions = {}) {
  const [conversationId] = useAtom(activeConversationIdAtom)
  const [, setStreaming] = useAtom(isStreamingAtom)
  const onUnauthorized = options.onUnauthorized ?? defaultOnUnauthorized

  const customFetch = useMemo(
    () => createChatFetch(onUnauthorized),
    [onUnauthorized],
  )

  // Load persisted history for the currently-active conversation. Disabled
  // when no conversation is selected — the UI treats that as "new chat".
  const historyQuery = useQuery({
    queryKey: ["conversation-messages", conversationId],
    queryFn: () => listConversationMessages(conversationId!),
    enabled: !!conversationId,
    staleTime: 30_000,
  })

  const loadedMessages = useMemo(
    () => toAiMessages(historyQuery.data?.content ?? []),
    [historyQuery.data],
  )

  const helpers = useAiChat({
    // Passing `id` reinitialises the hook's internal state when the user
    // switches conversations, giving us a clean slate for setMessages().
    id: conversationId ?? undefined,
    api: CHAT_SEND_ENDPOINT,
    fetch: customFetch,
    streamProtocol: "data",
    experimental_prepareRequestBody: ({ messages }) => {
      const last = messages[messages.length - 1] as
        | { content?: string; parts?: Array<{ type: string; text?: string }> }
        | undefined
      const content =
        typeof last?.content === "string" && last.content.length > 0
          ? last.content
          : (last?.parts
              ?.filter((p) => p.type === "text")
              .map((p) => p.text ?? "")
              .join("") ?? "")
      return conversationId ? { content, conversationId } : { content }
    },
  })

  // Sync loaded history into the chat hook exactly once per conversationId
  // switch. Guarded by a ref so subsequent re-renders (e.g. while streaming
  // new messages) do not clobber the live transcript with the stale fetched
  // copy. When the user switches away/back we re-sync from the fresh fetch.
  const syncedForIdRef = useRef<string | null>(null)
  useEffect(() => {
    if (!conversationId) {
      if (syncedForIdRef.current !== null) {
        helpers.setMessages([])
        syncedForIdRef.current = null
      }
      return
    }
    if (syncedForIdRef.current === conversationId) return
    if (!historyQuery.data) return
    helpers.setMessages(loadedMessages)
    syncedForIdRef.current = conversationId
  }, [conversationId, historyQuery.data, loadedMessages, helpers])

  useEffect(() => {
    const isBusy =
      helpers.status === "submitted" || helpers.status === "streaming"
    setStreaming(isBusy)
  }, [helpers.status, setStreaming])

  return helpers
}

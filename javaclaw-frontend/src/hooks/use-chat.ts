import type { Message } from "@ai-sdk/react"
import { useChat as useAiChat } from "@ai-sdk/react"
import { useQuery, useQueryClient } from "@tanstack/react-query"
import { useAtom } from "jotai"
import { useCallback, useEffect, useMemo, useRef, useState } from "react"

import { CHAT_SEND_ENDPOINT, cancelStream, createChatFetch } from "@/api/chat"
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
  const [conversationId, setConversationId] = useAtom(activeConversationIdAtom)
  const [, setStreaming] = useAtom(isStreamingAtom)
  const onUnauthorized = options.onUnauthorized ?? defaultOnUnauthorized
  const queryClient = useQueryClient()

  // chatSessionId is the stable key for useChat. It only changes when the user
  // explicitly switches conversations (sidebar click or "+"). It does NOT change
  // during lazy conversation creation to avoid reinitializing the hook mid-stream.
  // Each new chat gets a unique key to avoid AI SDK returning cached messages.
  const newSessionId = useCallback(() => `new-${crypto.randomUUID()}`, [])
  const [chatSessionId, setChatSessionId] = useState<string>(
    conversationId ?? newSessionId(),
  )

  // Track the server-assigned ID from lazy creation.
  const pendingIdRef = useRef<string | null>(null)

  // When user explicitly switches conversations (sidebar click or "+"),
  // sync chatSessionId. Skip when the atom update came from lazy creation
  // (pendingIdRef was just committed) — changing id mid-session kills messages.
  const justCommittedRef = useRef(false)
  useEffect(() => {
    if (justCommittedRef.current) {
      justCommittedRef.current = false
      return
    }
    if (conversationId) {
      setChatSessionId(conversationId)
      pendingIdRef.current = null
    } else {
      setChatSessionId(newSessionId())
      pendingIdRef.current = null
    }
  }, [conversationId])

  const customFetch = useMemo(() => {
    const baseFetch = createChatFetch(onUnauthorized)
    const wrappedFetch: typeof fetch = async (input, init) => {
      const response = await baseFetch(input, init)
      const serverId = response.headers.get("x-conversation-id")
      if (serverId && !conversationId) {
        pendingIdRef.current = serverId
      }
      return response
    }
    return wrappedFetch
  }, [onUnauthorized, conversationId])

  // Load persisted history for the currently-active conversation.
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
    id: chatSessionId,
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
    onFinish: () => {
      // Commit lazily-assigned conversation ID after stream ends.
      if (pendingIdRef.current) {
        justCommittedRef.current = true
        setConversationId(pendingIdRef.current)
        pendingIdRef.current = null
      }
      void queryClient.invalidateQueries({ queryKey: ["conversations"] })
    },
  })

  // Sync loaded history into the chat hook exactly once per conversation switch.
  // We track helpers via a ref to avoid re-triggering this effect on every render
  // (the helpers object from useAiChat is recreated each render).
  // IMPORTANT: We wait until chatSessionId matches conversationId before syncing
  // to avoid a race where setMessages() fires before useAiChat re-keys, causing
  // the hook reinit to wipe out the just-set messages.
  const syncedForIdRef = useRef<string | null>(null)
  const helpersRef = useRef(helpers)
  helpersRef.current = helpers
  useEffect(() => {
    if (!conversationId) {
      // "+" pressed with no pending lazy creation → clean slate
      if (!pendingIdRef.current) {
        helpersRef.current.stop()
        helpersRef.current.setMessages([])
      }
      syncedForIdRef.current = null
      return
    }
    // Wait until useAiChat has been re-keyed with the correct session ID.
    if (chatSessionId !== conversationId) return
    if (syncedForIdRef.current === conversationId) return
    if (!historyQuery.data) return
    helpersRef.current.setMessages(loadedMessages)
    syncedForIdRef.current = conversationId
  }, [conversationId, chatSessionId, historyQuery.data, loadedMessages])

  useEffect(() => {
    const isBusy =
      helpers.status === "submitted" || helpers.status === "streaming"
    setStreaming(isBusy)
  }, [helpers.status, setStreaming])

  // Wrap stop to also cancel the backend LLM stream, saving tokens.
  const wrappedStop = useCallback(() => {
    helpers.stop()
    const cid = conversationId ?? pendingIdRef.current
    if (cid) {
      cancelStream(cid).catch(() => {
        /* best-effort — backend stream will eventually time out */
      })
    }
  }, [helpers.stop, conversationId])

  return { ...helpers, stop: wrappedStop }
}

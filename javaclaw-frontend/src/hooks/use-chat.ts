import { useChat as useAiChat } from "@ai-sdk/react"
import { useAtom } from "jotai"
import { useEffect, useMemo } from "react"

import { CHAT_SEND_ENDPOINT, createChatFetch } from "@/api/chat"
import { activeConversationIdAtom, isStreamingAtom } from "@/store/chat"

interface UseJavaClawChatOptions {
  onUnauthorized?: () => void
}

function defaultOnUnauthorized() {
  if (typeof window !== "undefined" && window.location.pathname !== "/login") {
    window.location.href = "/login"
  }
}

export function useJavaClawChat(options: UseJavaClawChatOptions = {}) {
  const [conversationId] = useAtom(activeConversationIdAtom)
  const [, setStreaming] = useAtom(isStreamingAtom)
  const onUnauthorized = options.onUnauthorized ?? defaultOnUnauthorized

  const customFetch = useMemo(
    () => createChatFetch(onUnauthorized),
    [onUnauthorized],
  )

  const helpers = useAiChat({
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

  useEffect(() => {
    const isBusy =
      helpers.status === "submitted" || helpers.status === "streaming"
    setStreaming(isBusy)
  }, [helpers.status, setStreaming])

  return helpers
}

import { apiFetch, buildAuthHeaders } from "@/api/http"

export const CHAT_SEND_ENDPOINT = "/api/chat/send"

/** Cancels an active LLM stream on the backend, stopping token consumption. */
export async function cancelStream(conversationId: string): Promise<void> {
  await apiFetch(`/api/chat/cancel/${encodeURIComponent(conversationId)}`, {
    method: "POST",
  })
}

/**
 * Returns a fetch function for `useChat` that injects Basic Auth headers
 * and emits `401 -> /login` redirects via the shared http layer.
 */
export function createChatFetch(onUnauthorized: () => void): typeof fetch {
  return async (input, init) => {
    const headers = buildAuthHeaders(init?.headers)
    const response = await fetch(input, {
      ...init,
      headers,
    })
    if (response.status === 401) {
      onUnauthorized()
    }
    return response
  }
}

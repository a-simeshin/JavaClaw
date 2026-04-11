import { apiFetch } from "@/api/http"
import { getCsrfToken } from "@/lib/csrf"

export const CHAT_SEND_ENDPOINT = "/api/chat/send"

/** Cancels an active LLM stream on the backend, stopping token consumption. */
export async function cancelStream(conversationId: string): Promise<void> {
  await apiFetch(`/api/chat/cancel/${encodeURIComponent(conversationId)}`, {
    method: "POST",
  })
}

/**
 * Returns a fetch function for `useChat` that sends cookies + CSRF header
 * and emits `401 -> /login` redirects via the shared http layer.
 */
export function createChatFetch(onUnauthorized: () => void): typeof fetch {
  return async (input, init) => {
    const headers = new Headers(init?.headers)
    const method = (init?.method ?? "GET").toUpperCase()
    if (method !== "GET" && method !== "HEAD") {
      const csrf = getCsrfToken()
      if (csrf && !headers.has("X-XSRF-TOKEN")) {
        headers.set("X-XSRF-TOKEN", csrf)
      }
    }
    const response = await fetch(input, {
      ...init,
      headers,
      credentials: "include",
    })
    if (response.status === 401) {
      onUnauthorized()
    }
    return response
  }
}

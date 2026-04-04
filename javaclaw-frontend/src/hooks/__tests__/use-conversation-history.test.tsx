import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { renderHook, waitFor } from "@testing-library/react"
import type { ReactNode } from "react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { useConversationHistory } from "@/hooks/use-conversation-history"

const fetchMock = vi.fn()

function createWrapper() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    )
  }
}

beforeEach(() => {
  window.localStorage.setItem("javaclaw.auth.credentials", "abc")
  fetchMock.mockReset()
  vi.stubGlobal("fetch", fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
  window.localStorage.clear()
})

describe("useConversationHistory", () => {
  it("returns the fetched conversations", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [
            {
              id: "c1",
              title: "First",
              createdAt: "2026-04-01T00:00:00Z",
              updatedAt: "2026-04-01T00:00:00Z",
            },
          ],
          page: 0,
          size: 50,
          total: 1,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    )
    const { result } = renderHook(() => useConversationHistory(), {
      wrapper: createWrapper(),
    })
    await waitFor(() => expect(result.current.isLoading).toBe(false))
    expect(result.current.conversations).toHaveLength(1)
    expect(result.current.conversations[0].title).toBe("First")
  })

  it("surfaces load errors", async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 500 }))
    const { result } = renderHook(() => useConversationHistory(), {
      wrapper: createWrapper(),
    })
    await waitFor(() => expect(result.current.isError).toBe(true))
  })
})

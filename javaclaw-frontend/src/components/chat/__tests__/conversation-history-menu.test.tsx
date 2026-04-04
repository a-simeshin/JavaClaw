import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { render, screen, waitFor } from "@testing-library/react"
import { Provider as JotaiProvider, createStore } from "jotai"
import type { ReactNode } from "react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { ConversationHistoryMenu } from "@/components/chat/conversation-history-menu"
import "@/test/i18n-test"

const fetchMock = vi.fn()

function wrap(children: ReactNode) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  })
  const store = createStore()
  return (
    <JotaiProvider store={store}>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </JotaiProvider>
  )
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

describe("ConversationHistoryMenu", () => {
  it("renders an empty-state message when list is empty", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({ content: [], page: 0, size: 50, total: 0 }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    )
    render(wrap(<ConversationHistoryMenu />))
    await waitFor(() =>
      expect(screen.getByText(/No conversations yet/i)).toBeInTheDocument(),
    )
  })

  it("renders fetched conversations", async () => {
    fetchMock.mockResolvedValue(
      new Response(
        JSON.stringify({
          content: [
            {
              id: "c1",
              title: "Release notes",
              createdAt: "2026-04-04T00:00:00Z",
              updatedAt: "2026-04-04T00:00:00Z",
            },
          ],
          page: 0,
          size: 50,
          total: 1,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    )
    render(wrap(<ConversationHistoryMenu />))
    await waitFor(() =>
      expect(screen.getByText("Release notes")).toBeInTheDocument(),
    )
  })
})

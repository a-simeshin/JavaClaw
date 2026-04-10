import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { renderHook } from "@testing-library/react"
import { Provider as JotaiProvider, createStore, type Store } from "jotai"
import type { ReactNode } from "react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { unreadNotificationsAtom } from "@/store/tasks"
import { useTaskNotifications } from "@/hooks/use-task-notifications"

/* ---------- mock sonner ---------- */
vi.mock("sonner", () => ({
  toast: Object.assign(vi.fn(), {
    success: vi.fn(),
    error: vi.fn(),
  }),
}))
import { toast } from "sonner"

/* ---------- mock buildAuthHeaders ---------- */
vi.mock("@/api/http", () => ({
  buildAuthHeaders: () => {
    const h = new Headers()
    h.set("Authorization", "Basic dGVzdDp0ZXN0")
    return h
  },
}))

/* ---------- EventSource stub ---------- */
type ESListener = ((e: MessageEvent) => void) | null

class FakeEventSource {
  static instances: FakeEventSource[] = []
  url: string
  onmessage: ESListener = null
  onerror: (() => void) | null = null
  closed = false

  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }

  close() {
    this.closed = true
  }

  /** Helper — simulate a server-sent event */
  _emit(data: unknown) {
    this.onmessage?.(new MessageEvent("message", { data: JSON.stringify(data) }))
  }

  /** Helper — simulate a non-JSON heartbeat */
  _emitRaw(raw: string) {
    this.onmessage?.(new MessageEvent("message", { data: raw }))
  }
}

/* ---------- test helpers ---------- */
let queryClient: QueryClient
let store: Store

function createWrapper() {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <JotaiProvider store={store}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </JotaiProvider>
    )
  }
}

beforeEach(() => {
  FakeEventSource.instances = []
  vi.stubGlobal("EventSource", FakeEventSource)
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  store = createStore()
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.clearAllMocks()
})

describe("useTaskNotifications", () => {
  it("does not create EventSource when conversationId is null", () => {
    renderHook(() => useTaskNotifications(null), { wrapper: createWrapper() })
    expect(FakeEventSource.instances).toHaveLength(0)
  })

  it("creates EventSource with correct URL and auth param", () => {
    renderHook(() => useTaskNotifications("conv-1"), { wrapper: createWrapper() })
    expect(FakeEventSource.instances).toHaveLength(1)
    expect(FakeEventSource.instances[0].url).toBe(
      "/api/chat/notifications/conv-1?auth=Basic%20dGVzdDp0ZXN0",
    )
  })

  it("closes EventSource on unmount", () => {
    const { unmount } = renderHook(() => useTaskNotifications("conv-1"), {
      wrapper: createWrapper(),
    })
    const es = FakeEventSource.instances[0]
    expect(es.closed).toBe(false)
    unmount()
    expect(es.closed).toBe(true)
  })

  it("increments unread count on message", () => {
    renderHook(() => useTaskNotifications("conv-1"), { wrapper: createWrapper() })
    const es = FakeEventSource.instances[0]

    es._emit({ status: "completed", taskName: "Test" })
    expect(store.get(unreadNotificationsAtom)["conv-1"]).toBe(1)

    es._emit({ status: "failed", taskName: "Test2" })
    expect(store.get(unreadNotificationsAtom)["conv-1"]).toBe(2)
  })

  it("invalidates conversation messages query on message", () => {
    const spy = vi.spyOn(queryClient, "invalidateQueries")
    renderHook(() => useTaskNotifications("conv-1"), { wrapper: createWrapper() })

    FakeEventSource.instances[0]._emit({ status: "completed" })

    expect(spy).toHaveBeenCalledWith({
      queryKey: ["conversations", "conv-1", "messages"],
    })
  })

  it("shows success toast for completed status", () => {
    renderHook(() => useTaskNotifications("conv-1"), { wrapper: createWrapper() })
    FakeEventSource.instances[0]._emit({ status: "completed", taskName: "My Task" })
    expect(toast.success).toHaveBeenCalledWith('Task "My Task" completed')
  })

  it("shows error toast for failed status", () => {
    renderHook(() => useTaskNotifications("conv-1"), { wrapper: createWrapper() })
    FakeEventSource.instances[0]._emit({ status: "failed", taskName: "Bad Task" })
    expect(toast.error).toHaveBeenCalledWith('Task "Bad Task" failed')
  })

  it("shows persistent toast for awaiting_human_input status", () => {
    renderHook(() => useTaskNotifications("conv-1"), { wrapper: createWrapper() })
    FakeEventSource.instances[0]._emit({
      status: "awaiting_human_input",
      question: "Buy tickets?",
    })
    expect(toast).toHaveBeenCalledWith("Approval requested", {
      description: "Buy tickets?",
      duration: Infinity,
    })
  })

  it("uses fallback task name when taskName is missing", () => {
    renderHook(() => useTaskNotifications("conv-1"), { wrapper: createWrapper() })
    FakeEventSource.instances[0]._emit({ status: "completed" })
    expect(toast.success).toHaveBeenCalledWith('Task "Task" completed')
  })

  it("ignores non-JSON heartbeat messages", () => {
    renderHook(() => useTaskNotifications("conv-1"), { wrapper: createWrapper() })
    FakeEventSource.instances[0]._emitRaw(": heartbeat")
    expect(store.get(unreadNotificationsAtom)["conv-1"]).toBeUndefined()
  })

  it("closes old EventSource when conversationId changes", () => {
    const { rerender } = renderHook(
      ({ id }: { id: string }) => useTaskNotifications(id),
      { wrapper: createWrapper(), initialProps: { id: "conv-1" } },
    )
    const first = FakeEventSource.instances[0]

    rerender({ id: "conv-2" })

    expect(first.closed).toBe(true)
    expect(FakeEventSource.instances).toHaveLength(2)
    expect(FakeEventSource.instances[1].url).toContain("conv-2")
  })
})

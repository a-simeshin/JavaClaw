import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { renderHook, act } from "@testing-library/react"
import { Provider as JotaiProvider, createStore } from "jotai"
import type { ReactNode } from "react"
import { describe, expect, it, vi, beforeEach } from "vitest"

import { useResetClientState, CLIENT_RESET_ATOMS } from "@/lib/client-reset"
import {
  activeConversationIdAtom,
  isStreamingAtom,
  ACTIVE_CONVERSATION_INITIAL,
  IS_STREAMING_INITIAL,
} from "@/store/chat"
import {
  conversationSearchAtom,
  CONVERSATION_SEARCH_INITIAL,
} from "@/store/conversations"
import {
  notificationQueueAtom,
  NOTIFICATION_QUEUE_INITIAL,
  type TaskNotification,
} from "@/store/notifications"
import {
  pendingApprovalsAtom,
  activeTaskCountAtom,
  unreadNotificationsAtom,
  PENDING_APPROVALS_INITIAL,
  ACTIVE_TASK_COUNT_INITIAL,
  UNREAD_NOTIFICATIONS_INITIAL,
} from "@/store/tasks"

// -------------------------------------------------------------------------
// Helpers
// -------------------------------------------------------------------------

function makeWrapper(store: ReturnType<typeof createStore>, queryClient: QueryClient) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <JotaiProvider store={store}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </JotaiProvider>
    )
  }
}

// -------------------------------------------------------------------------
// Tests
// -------------------------------------------------------------------------

describe("useResetClientState", () => {
  let store: ReturnType<typeof createStore>
  let queryClient: QueryClient

  beforeEach(() => {
    store = createStore()
    queryClient = new QueryClient({
      defaultOptions: { queries: { retry: false } },
    })
  })

  it("resets activeConversationIdAtom to null", async () => {
    // Seed
    store.set(activeConversationIdAtom, "web-foo")
    expect(store.get(activeConversationIdAtom)).toBe("web-foo")

    const { result } = renderHook(() => useResetClientState(), {
      wrapper: makeWrapper(store, queryClient),
    })

    await act(async () => {
      result.current()
    })

    expect(store.get(activeConversationIdAtom)).toBe(ACTIVE_CONVERSATION_INITIAL)
    expect(store.get(activeConversationIdAtom)).toBeNull()
  })

  it("resets isStreamingAtom to false", async () => {
    store.set(isStreamingAtom, true)
    expect(store.get(isStreamingAtom)).toBe(true)

    const { result } = renderHook(() => useResetClientState(), {
      wrapper: makeWrapper(store, queryClient),
    })

    await act(async () => {
      result.current()
    })

    expect(store.get(isStreamingAtom)).toBe(IS_STREAMING_INITIAL)
    expect(store.get(isStreamingAtom)).toBe(false)
  })

  it("clears conversationSearchAtom to empty string", async () => {
    store.set(conversationSearchAtom, "hello")
    expect(store.get(conversationSearchAtom)).toBe("hello")

    const { result } = renderHook(() => useResetClientState(), {
      wrapper: makeWrapper(store, queryClient),
    })

    await act(async () => {
      result.current()
    })

    expect(store.get(conversationSearchAtom)).toBe(CONVERSATION_SEARCH_INITIAL)
    expect(store.get(conversationSearchAtom)).toBe("")
  })

  it("empties notificationQueueAtom", async () => {
    const mockNotification: TaskNotification = {
      id: "n1",
      taskId: "task-1",
      taskName: "My Task",
      status: "completed",
      message: "Done",
      conversationId: "conv-1",
      timestamp: new Date().toISOString(),
    }
    store.set(notificationQueueAtom, [mockNotification])
    expect(store.get(notificationQueueAtom)).toHaveLength(1)

    const { result } = renderHook(() => useResetClientState(), {
      wrapper: makeWrapper(store, queryClient),
    })

    await act(async () => {
      result.current()
    })

    expect(store.get(notificationQueueAtom)).toEqual(NOTIFICATION_QUEUE_INITIAL)
    expect(store.get(notificationQueueAtom)).toHaveLength(0)
  })

  it("empties pendingApprovalsAtom and resets activeTaskCountAtom to 0", async () => {
    // pendingApprovalsAtom — seed with a minimal approval shape
    store.set(pendingApprovalsAtom, [
      {
        id: "apr-1",
        taskId: "task-1",
        conversationId: "conv-1",
        question: "Proceed?",
        response: null,
        status: "pending",
        timeoutAt: new Date(Date.now() + 60_000).toISOString(),
        createdAt: new Date().toISOString(),
      },
    ])
    store.set(activeTaskCountAtom, 5)

    expect(store.get(pendingApprovalsAtom)).toHaveLength(1)
    expect(store.get(activeTaskCountAtom)).toBe(5)

    const { result } = renderHook(() => useResetClientState(), {
      wrapper: makeWrapper(store, queryClient),
    })

    await act(async () => {
      result.current()
    })

    expect(store.get(pendingApprovalsAtom)).toEqual(PENDING_APPROVALS_INITIAL)
    expect(store.get(pendingApprovalsAtom)).toHaveLength(0)
    expect(store.get(activeTaskCountAtom)).toBe(ACTIVE_TASK_COUNT_INITIAL)
    expect(store.get(activeTaskCountAtom)).toBe(0)
  })

  it("resets unreadNotificationsAtom to empty object", async () => {
    store.set(unreadNotificationsAtom, { "conv-1": 3, "conv-2": 1 })
    expect(Object.keys(store.get(unreadNotificationsAtom))).toHaveLength(2)

    const { result } = renderHook(() => useResetClientState(), {
      wrapper: makeWrapper(store, queryClient),
    })

    await act(async () => {
      result.current()
    })

    expect(store.get(unreadNotificationsAtom)).toEqual(UNREAD_NOTIFICATIONS_INITIAL)
    expect(store.get(unreadNotificationsAtom)).toEqual({})
  })

  it("calls queryClient.clear() exactly once", async () => {
    const clearSpy = vi.spyOn(queryClient, "clear")

    const { result } = renderHook(() => useResetClientState(), {
      wrapper: makeWrapper(store, queryClient),
    })

    await act(async () => {
      result.current()
    })

    expect(clearSpy).toHaveBeenCalledTimes(1)
  })
})

describe("CLIENT_RESET_ATOMS exhaustiveness", () => {
  it("contains all 7 per-user atoms (checked by atom reference)", () => {
    const atomsInList = CLIENT_RESET_ATOMS.map(([atom]) => atom)

    expect(atomsInList).toContain(activeConversationIdAtom)
    expect(atomsInList).toContain(isStreamingAtom)
    expect(atomsInList).toContain(conversationSearchAtom)
    expect(atomsInList).toContain(notificationQueueAtom)
    expect(atomsInList).toContain(pendingApprovalsAtom)
    expect(atomsInList).toContain(activeTaskCountAtom)
    expect(atomsInList).toContain(unreadNotificationsAtom)

    // Hard-count guard: if a new atom is added to the hook without adding it
    // here, this test will fail and force the author to update the list.
    expect(CLIENT_RESET_ATOMS).toHaveLength(7)
  })

  it("each atom's initial value in CLIENT_RESET_ATOMS matches the exported constant", () => {
    const map = new Map(CLIENT_RESET_ATOMS.map(([atom, init]) => [atom, init]))

    expect(map.get(activeConversationIdAtom)).toBe(ACTIVE_CONVERSATION_INITIAL)
    expect(map.get(isStreamingAtom)).toBe(IS_STREAMING_INITIAL)
    expect(map.get(conversationSearchAtom)).toBe(CONVERSATION_SEARCH_INITIAL)
    expect(map.get(notificationQueueAtom)).toBe(NOTIFICATION_QUEUE_INITIAL)
    expect(map.get(pendingApprovalsAtom)).toBe(PENDING_APPROVALS_INITIAL)
    expect(map.get(activeTaskCountAtom)).toBe(ACTIVE_TASK_COUNT_INITIAL)
    expect(map.get(unreadNotificationsAtom)).toBe(UNREAD_NOTIFICATIONS_INITIAL)
  })
})

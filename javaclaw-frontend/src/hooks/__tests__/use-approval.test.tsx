import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { renderHook, act } from "@testing-library/react"
import type { ReactNode } from "react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import type { ApprovalRequestDto } from "@/api/approvals"
import { useApproval } from "@/hooks/use-approval"

/* ---------- mock sonner ---------- */
vi.mock("sonner", () => ({
  toast: Object.assign(vi.fn(), {
    success: vi.fn(),
    error: vi.fn(),
  }),
}))
import { toast } from "sonner"

/* ---------- mock api/approvals ---------- */
const respondToApprovalMock = vi.fn()
vi.mock("@/api/approvals", async () => {
  const actual = await vi.importActual<typeof import("@/api/approvals")>("@/api/approvals")
  return { ...actual, respondToApproval: (...args: unknown[]) => respondToApprovalMock(...args) }
})

/* ---------- helpers ---------- */
let queryClient: QueryClient

function createWrapper() {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  }
}

function pendingApproval(overrides: Partial<ApprovalRequestDto> = {}): ApprovalRequestDto {
  return {
    id: "apr-1",
    taskId: "task-1",
    conversationId: "conv-1",
    question: "Buy tickets for 9500?",
    response: null,
    status: "pending",
    timeoutAt: new Date(Date.now() + 60_000).toISOString(),
    createdAt: new Date().toISOString(),
    respondedAt: null,
    ...overrides,
  }
}

beforeEach(() => {
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  respondToApprovalMock.mockReset()
  vi.clearAllMocks()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe("useApproval", () => {
  it("returns null resolved for pending approval", () => {
    const { result } = renderHook(() => useApproval(pendingApproval()), {
      wrapper: createWrapper(),
    })
    expect(result.current.resolved).toBeNull()
    expect(result.current.isSubmitting).toBe(false)
    expect(result.current.isExpired).toBe(false)
  })

  it("returns pre-resolved state for non-pending approval", () => {
    const { result } = renderHook(
      () => useApproval(pendingApproval({ status: "approved" })),
      { wrapper: createWrapper() },
    )
    expect(result.current.resolved).toBe("approved")
  })

  it("approve() calls API and sets resolved to approved", async () => {
    respondToApprovalMock.mockResolvedValueOnce(undefined)
    const { result } = renderHook(() => useApproval(pendingApproval()), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.approve()
    })

    expect(respondToApprovalMock).toHaveBeenCalledWith("apr-1", "да")
    expect(result.current.resolved).toBe("approved")
    expect(toast.success).toHaveBeenCalledWith("Approved")
  })

  it("deny() calls API and sets resolved to denied", async () => {
    respondToApprovalMock.mockResolvedValueOnce(undefined)
    const { result } = renderHook(() => useApproval(pendingApproval()), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.deny()
    })

    expect(respondToApprovalMock).toHaveBeenCalledWith("apr-1", "нет")
    expect(result.current.resolved).toBe("denied")
    expect(toast.success).toHaveBeenCalledWith("Denied")
  })

  it("respond() with custom text calls API with that text", async () => {
    respondToApprovalMock.mockResolvedValueOnce(undefined)
    const { result } = renderHook(() => useApproval(pendingApproval()), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.respond("Only economy class")
    })

    expect(respondToApprovalMock).toHaveBeenCalledWith("apr-1", "Only economy class")
    expect(result.current.resolved).toBe("denied") // non-approval phrase → denied
  })

  it("respond() with approval phrase sets resolved to approved", async () => {
    respondToApprovalMock.mockResolvedValueOnce(undefined)
    const { result } = renderHook(() => useApproval(pendingApproval()), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.respond("yes please")
    })

    expect(result.current.resolved).toBe("approved")
  })

  it("invalidates pending approvals query on success", async () => {
    respondToApprovalMock.mockResolvedValueOnce(undefined)
    const spy = vi.spyOn(queryClient, "invalidateQueries")
    const { result } = renderHook(() => useApproval(pendingApproval()), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.approve()
    })

    expect(spy).toHaveBeenCalledWith({ queryKey: ["approvals", "pending"] })
  })

  it("shows error toast on API failure", async () => {
    respondToApprovalMock.mockRejectedValueOnce(new Error("Network error"))
    const { result } = renderHook(() => useApproval(pendingApproval()), {
      wrapper: createWrapper(),
    })

    await act(async () => {
      await result.current.approve()
    })

    expect(result.current.resolved).toBeNull()
    expect(toast.error).toHaveBeenCalledWith("Failed to submit response")
  })

  it("prevents double submission", async () => {
    let resolveApi: () => void
    respondToApprovalMock.mockImplementation(
      () => new Promise<void>((r) => { resolveApi = r }),
    )
    const { result } = renderHook(() => useApproval(pendingApproval()), {
      wrapper: createWrapper(),
    })

    // Start first submission
    let firstDone = false
    act(() => {
      result.current.approve().then(() => { firstDone = true })
    })
    expect(result.current.isSubmitting).toBe(true)

    // Try second while first is in-flight
    await act(async () => {
      await result.current.deny()
    })

    // Only one API call
    expect(respondToApprovalMock).toHaveBeenCalledTimes(1)

    // Complete the first
    await act(async () => {
      resolveApi!()
      await vi.waitFor(() => expect(firstDone).toBe(true))
    })
  })

  it("does not submit when already resolved", async () => {
    const { result } = renderHook(
      () => useApproval(pendingApproval({ status: "denied" })),
      { wrapper: createWrapper() },
    )

    await act(async () => {
      await result.current.approve()
    })

    expect(respondToApprovalMock).not.toHaveBeenCalled()
  })

  it("detects expired approval and sets timeout", () => {
    const { result } = renderHook(
      () =>
        useApproval(
          pendingApproval({
            timeoutAt: new Date(Date.now() - 1000).toISOString(),
          }),
        ),
      { wrapper: createWrapper() },
    )
    // Once resolved is set to "timeout", isExpired becomes false (isExpired = !resolved && ...)
    expect(result.current.resolved).toBe("timeout")
  })
})

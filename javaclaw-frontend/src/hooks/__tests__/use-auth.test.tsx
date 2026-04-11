import { renderHook, act, waitFor } from "@testing-library/react"
import { Provider as JotaiProvider, createStore } from "jotai"
import type { ReactNode } from "react"
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest"

import { useAuth } from "@/hooks/use-auth"

function wrapper({ children }: { children: ReactNode }) {
  const store = createStore()
  return <JotaiProvider store={store}>{children}</JotaiProvider>
}

const fetchMock = vi.fn()

beforeEach(() => {
  fetchMock.mockReset()
  vi.stubGlobal("fetch", fetchMock)
  // Suppress XSRF cookie check — no cookie, no token, ensureCsrfCookie will call fetch
  Object.defineProperty(document, "cookie", {
    get: () => "",
    configurable: true,
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
  vi.restoreAllMocks()
})

/** Build a minimal UserInfo response */
function makeUserInfo(overrides?: object) {
  return {
    id: "1",
    username: "alice",
    email: null,
    roles: ["ADMIN"],
    authorities: ["ROLE_ADMIN"],
    ...overrides,
  }
}

describe("useAuth", () => {
  it("sets user on successful login", async () => {
    // ensureCsrfCookie GET → no-content
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    // getMe (mount) → 401 so isLoading settles to false / null user
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))
    // login POST → LoginResponse
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          user: makeUserInfo(),
          sessionExpiresAt: "2099-01-01T00:00:00Z",
        }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    )

    const { result } = renderHook(() => useAuth(), { wrapper })

    // Wait for mount effect to settle
    await waitFor(() => expect(result.current.auth.isLoading).toBe(false))

    await act(async () => {
      await result.current.login("alice", "secret")
    })

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user?.username).toBe("alice")
  })

  it("sets isAuthenticated true when /api/auth/me resolves on mount", async () => {
    // ensureCsrfCookie → already has cookie (skip fetch)
    Object.defineProperty(document, "cookie", {
      get: () => "XSRF-TOKEN=abc123",
      configurable: true,
    })
    // getMe
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify(makeUserInfo()), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    )

    const { result } = renderHook(() => useAuth(), { wrapper })

    await waitFor(() => expect(result.current.isAuthenticated).toBe(true))
    expect(result.current.user?.username).toBe("alice")
  })

  it("clears user on logout", async () => {
    Object.defineProperty(document, "cookie", {
      get: () => "XSRF-TOKEN=abc123",
      configurable: true,
    })
    // getMe on mount
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify(makeUserInfo()), {
        status: 200,
        headers: { "Content-Type": "application/json" },
      }),
    )
    // logout POST
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))

    const { result } = renderHook(() => useAuth(), { wrapper })
    await waitFor(() => expect(result.current.isAuthenticated).toBe(true))

    await act(async () => {
      await result.current.logout()
    })

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
  })

  it("stays unauthenticated when /api/auth/me returns 401 on mount", async () => {
    // ensureCsrfCookie
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    // getMe → 401
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 401 }))

    const { result } = renderHook(() => useAuth(), { wrapper })

    await waitFor(() => expect(result.current.auth.isLoading).toBe(false))

    expect(result.current.isAuthenticated).toBe(false)
    expect(result.current.user).toBeNull()
  })
})

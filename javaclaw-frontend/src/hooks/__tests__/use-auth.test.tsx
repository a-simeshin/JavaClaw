import { renderHook, act } from "@testing-library/react"
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
  window.localStorage.clear()
  fetchMock.mockReset()
  vi.stubGlobal("fetch", fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe("useAuth", () => {
  it("persists credentials on successful login", async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(
        JSON.stringify({ username: "alice", role: "ADMIN" }),
        { status: 200, headers: { "Content-Type": "application/json" } },
      ),
    )

    const { result } = renderHook(() => useAuth(), { wrapper })

    await act(async () => {
      await result.current.login("alice", "secret")
    })

    expect(result.current.isAuthenticated).toBe(true)
    expect(result.current.user.username).toBe("alice")
    expect(result.current.user.role).toBe("ADMIN")
    expect(
      window.localStorage.getItem("javaclaw.auth.credentials"),
    ).toBe(btoa("alice:secret"))
  })

  it("clears credentials on logout", async () => {
    window.localStorage.setItem("javaclaw.auth.credentials", "abc")
    window.localStorage.setItem("javaclaw.auth.username", "bob")
    const { result } = renderHook(() => useAuth(), { wrapper })

    act(() => {
      result.current.logout()
    })

    expect(result.current.isAuthenticated).toBe(false)
    expect(
      window.localStorage.getItem("javaclaw.auth.credentials"),
    ).toBeNull()
  })
})

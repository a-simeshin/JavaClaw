/**
 * Integration tests for AppHeader — logout flow.
 *
 * Mocks: useAuth, useNavigate, useResetClientState, useTheme, react-i18next,
 *        @/store/auth (authUserAtom), @/i18n (setLanguage).
 * Does NOT mock @/lib/client-reset's implementation — only its hook export.
 */
import { render, screen, fireEvent, waitFor } from "@testing-library/react"
import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { Provider as JotaiProvider, createStore } from "jotai"
import type { ReactNode } from "react"
import { describe, expect, it, vi, beforeEach } from "vitest"

// ---------------------------------------------------------------------------
// Module-level mocks (hoisted by Vitest)
// ---------------------------------------------------------------------------
// ALL values referenced inside vi.mock() factories must be created with
// vi.hoisted() because vi.mock() calls are hoisted to the top of the file
// before any const/let declarations are initialized.

const { mockNavigate, mockLogout, mockResetClientState, seededAuthUserAtom } =
  vi.hoisted(() => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { atom } = require("jotai") as typeof import("jotai")
    return {
      mockNavigate: vi.fn(),
      mockLogout: vi.fn(),
      mockResetClientState: vi.fn(),
      seededAuthUserAtom: atom({ username: "admin", role: "ADMIN" } as {
        username: string
        role?: string
      } | null),
    }
  })

vi.mock("@tanstack/react-router", () => ({
  useNavigate: () => mockNavigate,
}))

vi.mock("@/hooks/use-auth", () => ({
  useAuth: () => ({ logout: mockLogout, user: { username: "admin", role: "ADMIN" } }),
}))

vi.mock("@/lib/client-reset", () => ({
  useResetClientState: () => mockResetClientState,
}))

vi.mock("@/hooks/use-theme", () => ({
  useTheme: () => ({ resolved: "light", setTheme: vi.fn() }),
}))

vi.mock("react-i18next", () => ({
  useTranslation: () => ({
    t: (key: string) => key,
    i18n: { resolvedLanguage: "en" },
  }),
}))

vi.mock("@/i18n", () => ({
  setLanguage: vi.fn(),
}))

// Mock Radix DropdownMenu with simple inline passthrough components so that
// the menu content is always rendered in the DOM (no portal, no pointer-events
// restrictions) and fireEvent.click works reliably in jsdom.
vi.mock("@/components/ui/dropdown-menu", () => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const React = require("react") as typeof import("react")
  return {
    DropdownMenu: ({ children }: { children: React.ReactNode }) =>
      React.createElement(React.Fragment, null, children),
    DropdownMenuTrigger: ({
      children,
      ...props
    }: React.ButtonHTMLAttributes<HTMLButtonElement> & { children?: React.ReactNode }) =>
      React.createElement("button", { type: "button", ...props }, children),
    DropdownMenuContent: ({ children }: { children: React.ReactNode }) =>
      React.createElement("div", { "data-testid": "dropdown-content" }, children),
    DropdownMenuLabel: ({ children }: { children: React.ReactNode }) =>
      React.createElement("div", null, children),
    DropdownMenuSeparator: () => React.createElement("hr"),
    DropdownMenuItem: ({
      children,
      onClick,
    }: { children?: React.ReactNode; onClick?: React.MouseEventHandler }) =>
      React.createElement("button", { type: "button", onClick }, children),
  }
})

// authUserAtom is read with useAtomValue inside AppHeader.
// We need the atom to have a working value. We mock the module so
// authUserAtom is a plain Jotai atom pre-seeded with admin data.
vi.mock("@/store/auth", () => ({
  authUserAtom: seededAuthUserAtom,
}))

// ---------------------------------------------------------------------------
// Import the component AFTER all mocks are set up
// ---------------------------------------------------------------------------
import { AppHeader } from "@/components/app-header"

// ---------------------------------------------------------------------------
// Wrapper
// ---------------------------------------------------------------------------

function makeWrapper() {
  const store = createStore()
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return function Wrapper({ children }: { children: ReactNode }) {
    return (
      <JotaiProvider store={store}>
        <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
      </JotaiProvider>
    )
  }
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("AppHeader — logout flow", () => {
  beforeEach(() => {
    mockLogout.mockReset()
    mockResetClientState.mockReset()
    mockNavigate.mockReset()
  })

  it("logout click calls logout(), resetClientState, then navigate to /login", async () => {
    mockLogout.mockResolvedValueOnce(undefined)

    render(<AppHeader />, { wrapper: makeWrapper() })

    // Open the user dropdown
    const userMenuTrigger = screen.getByRole("button", { name: "header.userMenu" })
    fireEvent.click(userMenuTrigger)

    // Click the sign-out item (text = i18n key "actions.signOut")
    const signOutItem = await screen.findByText("actions.signOut")
    fireEvent.click(signOutItem)

    await waitFor(() => {
      expect(mockLogout).toHaveBeenCalledTimes(1)
    })

    await waitFor(() => {
      expect(mockResetClientState).toHaveBeenCalledTimes(1)
    })

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith({ to: "/login" })
    })
  })

  it("logout navigates even when logout() throws (finally block)", async () => {
    mockLogout.mockRejectedValueOnce(new Error("network error"))

    render(<AppHeader />, { wrapper: makeWrapper() })

    const userMenuTrigger = screen.getByRole("button", { name: "header.userMenu" })
    fireEvent.click(userMenuTrigger)

    const signOutItem = await screen.findByText("actions.signOut")
    fireEvent.click(signOutItem)

    // Even though logout() rejected, the finally block must run
    await waitFor(() => {
      expect(mockResetClientState).toHaveBeenCalledTimes(1)
    })

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith({ to: "/login" })
    })
  })

  it("resetClientState is called before navigate", async () => {
    mockLogout.mockResolvedValueOnce(undefined)

    const callOrder: string[] = []
    mockResetClientState.mockImplementation(() => { callOrder.push("reset") })
    mockNavigate.mockImplementation(() => { callOrder.push("navigate") })

    render(<AppHeader />, { wrapper: makeWrapper() })

    const userMenuTrigger = screen.getByRole("button", { name: "header.userMenu" })
    fireEvent.click(userMenuTrigger)

    const signOutItem = await screen.findByText("actions.signOut")
    fireEvent.click(signOutItem)

    await waitFor(() => {
      expect(callOrder).toContain("navigate")
    })

    expect(callOrder.indexOf("reset")).toBeLessThan(callOrder.indexOf("navigate"))
  })
})

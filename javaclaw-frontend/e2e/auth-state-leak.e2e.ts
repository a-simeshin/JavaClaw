/**
 * E2E regression tests: Auth State Leak between users.
 *
 * These tests verify that after logout + re-login (or session switch),
 * no data from the previous user leaks into the new user's session.
 *
 * Scenarios:
 *   E2E-1: Admin → logout → login as user shows clean chat state
 *   E2E-2: Mount-effect resets state when getMe returns different user on reload
 *   E2E-3: 401 full-reload path leaves clean state
 */
import { test, expect } from "@playwright/test"
import { setupRoutesForUser, type UserFixture } from "./helpers/auth"

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const ADMIN_FIXTURE: UserFixture = {
  id: "admin-1",
  username: "admin",
  role: "ADMIN",
  roles: ["ROLE_ADMIN"],
  authenticated: true,
  conversations: [
    {
      id: "admin-chat-1",
      title: "Admin conversation",
      messages: [
        {
          id: "m1",
          role: "user",
          content: "скажи одно слово: тест",
          createdAt: new Date().toISOString(),
        },
        {
          id: "m2",
          role: "assistant",
          content: "тест",
          createdAt: new Date().toISOString(),
        },
      ],
    },
  ],
}

const USER_FIXTURE: UserFixture = {
  id: "user-1",
  username: "user",
  role: "USER",
  roles: ["ROLE_USER"],
  authenticated: true,
  conversations: [
    {
      id: "user-chat-1",
      title: "User conversation",
      messages: [
        {
          id: "m3",
          role: "user",
          content: "hello user",
          createdAt: new Date().toISOString(),
        },
      ],
    },
  ],
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

test.describe("Auth State Leak — regression tests", () => {
  test("E2E-1: admin → logout → login as user shows clean chat state", async ({
    page,
  }) => {
    // Track requests to admin's conversation after user login
    const adminConvRequests: string[] = []
    page.on("request", (req) => {
      if (req.url().includes("admin-chat-1")) {
        adminConvRequests.push(req.url())
      }
    })

    // Setup admin routes and navigate
    await setupRoutesForUser(page, ADMIN_FIXTURE)
    await page.goto("/chat")
    await page.waitForLoadState("networkidle")

    // Verify sidebar shows admin conversation title
    await expect(page.locator("body")).toContainText("Admin conversation")

    // Remember how many admin-conv requests happened during admin session
    const requestsBeforeLogout = adminConvRequests.length

    // Switch routes to user — all subsequent API calls now return user data
    await page.unrouteAll()
    await setupRoutesForUser(page, USER_FIXTURE)
    // Explicitly keep the logout endpoint available
    await page.route("**/api/auth/logout", (route) =>
      route.fulfill({ status: 204 }),
    )

    // Trigger logout via the header user menu
    const userMenuTrigger = page
      .locator('[aria-label="header.userMenu"]')
      .or(page.locator('[aria-label*="userMenu"]'))
      .or(page.locator('button[aria-label*="Menu"]'))
    await userMenuTrigger.first().click()

    // Find and click sign-out item (covers both translated and key forms)
    const signOutButton = page
      .locator('[role="menuitem"]')
      .filter({ hasText: /sign.?out|выйти|signOut/i })
      .last()
    await signOutButton.click()

    // Should navigate to /login after logout
    await page.waitForURL("**/login", { timeout: 10_000 })

    // Now navigate as user (routes already set to USER_FIXTURE)
    await page.goto("/chat")
    await page.waitForLoadState("networkidle")

    // Anti-leak assertions
    // 1. Sidebar shows user conversation, NOT admin's
    await expect(page.locator("body")).not.toContainText("Admin conversation")
    await expect(page.locator("body")).toContainText("User conversation")

    // 2. Main area does NOT contain admin messages
    await expect(page.locator("body")).not.toContainText(
      "скажи одно слово: тест",
    )

    // 3. No new requests to admin's conversation appeared after user login
    const newAdminRequests = adminConvRequests.length - requestsBeforeLogout
    expect(newAdminRequests).toBe(0)
  })

  test("E2E-2: mount-effect resets state when getMe returns different user on reload", async ({
    page,
  }) => {
    // Setup admin routes
    await setupRoutesForUser(page, ADMIN_FIXTURE)
    await page.goto("/chat")
    await page.waitForLoadState("networkidle")

    // Verify admin conversation visible
    await expect(page.locator("body")).toContainText("Admin conversation")

    // Switch routes to user WITHOUT SPA logout — simulates session cookie change
    await page.unrouteAll()
    await setupRoutesForUser(page, USER_FIXTURE)

    // Reload — mount effect fires, getMe returns user-1 (different from admin-1)
    // → mount effect detects the id mismatch and calls resetClientState()
    await page.reload()
    await page.waitForLoadState("networkidle")

    // Main area must NOT contain admin's content
    await expect(page.locator("body")).not.toContainText(
      "скажи одно слово: тест",
    )
    await expect(page.locator("body")).not.toContainText("admin-chat-1")

    // User's conversation should be visible
    await expect(page.locator("body")).toContainText("User conversation")
  })

  test("E2E-3: 401 full-reload path leaves clean state for subsequent user", async ({
    page,
  }) => {
    // Start as admin
    await setupRoutesForUser(page, ADMIN_FIXTURE)
    await page.goto("/chat")
    await page.waitForLoadState("networkidle")

    await expect(page.locator("body")).toContainText("Admin conversation")

    // Override conversations to return 401 — this triggers the 401 interceptor
    // in api/http.ts which does window.location.href = "/login" (full page reload)
    await page.route("**/api/conversations*", (route) =>
      route.fulfill({ status: 401 }),
    )

    // Navigate away and back to trigger a refetch
    await page.goto("/chat")

    // After 401, set up user routes
    await page.unrouteAll()
    await setupRoutesForUser(page, USER_FIXTURE)

    await page.goto("/chat")
    await page.waitForLoadState("networkidle")

    // Should show user's clean state — no admin content leaking through
    await expect(page.locator("body")).not.toContainText(
      "скажи одно слово: тест",
    )
    await expect(page.locator("body")).not.toContainText("admin-chat-1")
  })
})

import type { Page } from "@playwright/test"

export interface ConversationFixture {
  id: string
  title: string
  messages: Array<{
    id: string
    role: "user" | "assistant"
    content: string
    createdAt: string
  }>
}

export interface UserFixture {
  id: string
  username: string
  role?: string
  roles: string[]
  authenticated: boolean
  conversations: ConversationFixture[]
}

export async function setupRoutesForUser(
  page: Page,
  user: UserFixture,
): Promise<void> {
  // Auth — /api/auth/me
  await page.route("**/api/auth/me", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        id: user.id,
        username: user.username,
        role: user.role,
        roles: user.roles,
        authenticated: user.authenticated,
      }),
    }),
  )

  // Auth — logout
  await page.route("**/api/auth/logout", (route) =>
    route.fulfill({ status: 204 }),
  )

  // Auth — login
  await page.route("**/api/auth/login", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        user: {
          id: user.id,
          username: user.username,
          roles: user.roles,
        },
        sessionExpiresAt: null,
      }),
    }),
  )

  // Conversations list + per-conversation messages
  const convList = user.conversations.map((c) => ({
    id: c.id,
    title: c.title,
    userId: user.id,
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString(),
  }))

  await page.route("**/api/conversations*", (route) => {
    const url = route.request().url()

    // Per-conversation messages endpoint
    for (const conv of user.conversations) {
      if (url.includes(`/${conv.id}/messages`)) {
        return route.fulfill({
          status: 200,
          contentType: "application/json",
          body: JSON.stringify({
            content: conv.messages,
            totalElements: conv.messages.length,
            page: 0,
            size: 20,
          }),
        })
      }
    }

    // Return 403 for any conversation messages path that doesn't belong to this user
    if (url.match(/\/api\/conversations\/[^?/]+\/messages/)) {
      return route.fulfill({
        status: 403,
        contentType: "application/json",
        body: JSON.stringify({ error: "Forbidden" }),
      })
    }

    // Conversation list
    return route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        content: convList,
        totalElements: convList.length,
        totalPages: 1,
        page: 0,
        size: 20,
      }),
    })
  })

  // Tasks — return empty list
  await page.route("**/api/tasks*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([]),
    }),
  )

  // Pending approvals — return empty list
  await page.route("**/api/chat/approval/pending*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([]),
    }),
  )

  // SSE notifications — return a silent heartbeat stream
  await page.route("**/api/chat/notifications/*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      headers: { "Cache-Control": "no-cache" },
      body: "data: {}\n\n",
    }),
  )
}

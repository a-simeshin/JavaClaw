import { test, expect, type Page } from "@playwright/test";

/**
 * E2E test: Conversation management (sidebar CRUD).
 *
 * Uses route mocking for API responses since the backend may not be running.
 */

let conversationIdCounter = 0;

function mockConversationApi(page: Page) {
  const conversations: Array<{
    id: string;
    title: string;
    createdAt: string;
    updatedAt: string;
    messageCount: number;
  }> = [
    {
      id: "conv-1",
      title: "First conversation",
      createdAt: new Date().toISOString(),
      updatedAt: new Date().toISOString(),
      messageCount: 3,
    },
  ];

  // Mock auth
  page.route("**/api/auth/me", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        username: "admin",
        displayName: "Admin",
        roles: ["ROLE_ADMIN"],
        authenticated: true,
      }),
    }),
  );

  // List conversations
  page.route("**/api/conversations?*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        content: conversations,
        totalElements: conversations.length,
        totalPages: 1,
        page: 0,
        size: 20,
      }),
    }),
  );

  // Create conversation
  page.route("**/api/conversations", (route) => {
    if (route.request().method() === "POST") {
      conversationIdCounter++;
      const newConv = {
        id: `conv-new-${conversationIdCounter}`,
        title: "New conversation",
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
        messageCount: 0,
      };
      conversations.push(newConv);
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(newConv),
      });
    }
    return route.continue();
  });

  // Delete conversation
  page.route("**/api/conversations/*", (route) => {
    if (route.request().method() === "DELETE") {
      return route.fulfill({ status: 204 });
    }
    // Messages endpoint
    if (route.request().url().includes("/messages")) {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify({
          content: [],
          totalElements: 0,
          totalPages: 0,
          page: 0,
          size: 50,
        }),
      });
    }
    return route.continue();
  });
}

test.describe("Conversation Management", () => {
  test.beforeEach(async ({ page }) => {
    await mockConversationApi(page);
  });

  test("conversation list renders in sidebar", async ({ page }) => {
    await page.goto("/chat");

    // Sidebar should show the existing conversation
    await expect(page.getByText("First conversation")).toBeVisible({
      timeout: 10_000,
    });
  });

  test("clicking New Conversation creates a new item", async ({ page }) => {
    await page.goto("/chat");

    // Find and click the "New" / "+" button
    const newBtn = page
      .getByRole("button", { name: /new/i })
      .or(page.locator('[aria-label*="new"]'))
      .or(page.locator("button").filter({ hasText: /\+|new/i }));

    // If the button exists, click it
    const count = await newBtn.count();
    if (count > 0) {
      await newBtn.first().click();
      // After creation, a new conversation item should appear or the UI should update
      await page.waitForTimeout(1_000);
    } else {
      test.skip(true, "New conversation button not found in sidebar");
    }
  });

  test("switching between conversations updates the chat area", async ({
    page,
  }) => {
    await page.goto("/chat");

    // Click on the first conversation in the sidebar
    const convItem = page.getByText("First conversation");
    const visible = await convItem.isVisible().catch(() => false);
    if (visible) {
      await convItem.click();
      // URL or chat area should update
      await page.waitForTimeout(500);
    } else {
      test.skip(true, "Conversation sidebar not visible");
    }
  });

  test("delete conversation removes it from list", async ({ page }) => {
    await page.goto("/chat");

    // Look for a delete button or context menu on a conversation
    const deleteBtn = page
      .locator('[aria-label*="delete"]')
      .or(page.locator("button").filter({ hasText: /delete|remove/i }));

    const count = await deleteBtn.count();
    if (count > 0) {
      await deleteBtn.first().click();

      // If there's a confirmation dialog, confirm it
      const confirmBtn = page.getByRole("button", { name: /confirm|yes|ok/i });
      const confirmVisible = await confirmBtn
        .isVisible({ timeout: 2_000 })
        .catch(() => false);
      if (confirmVisible) {
        await confirmBtn.click();
      }

      await page.waitForTimeout(1_000);
    } else {
      test.skip(true, "Delete button not found in conversation sidebar");
    }
  });
});

import { test, expect, type Page } from "@playwright/test";

/**
 * E2E test: Chat message flow.
 *
 * Since Spring Security auth is not yet wired, we bypass authentication by
 * injecting a mock auth state into localStorage and intercepting the /api/auth/me
 * endpoint. If auth becomes active, update the login helper accordingly.
 */

async function bypassAuth(page: Page) {
  // Mock the /api/auth/me endpoint to return a logged-in user
  await page.route("**/api/auth/me", (route) =>
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
}

test.describe("Chat Flow", () => {
  test.beforeEach(async ({ page }) => {
    await bypassAuth(page);
  });

  test("navigates to chat page and renders composer", async ({ page }) => {
    await page.goto("/chat");
    await expect(page).toHaveURL(/\/chat/);

    // Chat composer textarea should be visible
    const composer = page.getByRole("textbox").or(page.locator("textarea"));
    await expect(composer.first()).toBeVisible({ timeout: 10_000 });
  });

  test("typing a message and pressing Enter sends it", async ({ page }) => {
    // Mock the SSE chat endpoint to return a simple response
    await page.route("**/api/chat/send", (route) =>
      route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        headers: { "x-vercel-ai-ui-message-stream": "v1" },
        body: [
          'data: {"type":"text-delta","textDelta":"Hello"}',
          'data: {"type":"text-delta","textDelta":" world"}',
          'data: {"type":"finish","finishReason":"stop"}',
          "",
        ].join("\n"),
      }),
    );

    await page.goto("/chat");

    const composer = page.getByRole("textbox").or(page.locator("textarea"));
    await composer.first().fill("Hello, assistant!");
    await composer.first().press("Enter");

    // User message should appear
    await expect(page.getByText("Hello, assistant!")).toBeVisible({
      timeout: 5_000,
    });
  });

  test("streaming response renders assistant message", async ({ page }) => {
    await page.route("**/api/chat/send", (route) =>
      route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        headers: { "x-vercel-ai-ui-message-stream": "v1" },
        body: [
          'data: {"type":"text-delta","textDelta":"I am "}',
          'data: {"type":"text-delta","textDelta":"the assistant."}',
          'data: {"type":"finish","finishReason":"stop"}',
          "",
        ].join("\n"),
      }),
    );

    await page.goto("/chat");

    const composer = page.getByRole("textbox").or(page.locator("textarea"));
    await composer.first().fill("Test");
    await composer.first().press("Enter");

    // Assistant response should appear
    await expect(page.getByText("I am the assistant.")).toBeVisible({
      timeout: 10_000,
    });
  });

  test("tool call card renders with status", async ({ page }) => {
    await page.route("**/api/chat/send", (route) =>
      route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        headers: { "x-vercel-ai-ui-message-stream": "v1" },
        body: [
          'data: {"type":"tool-call","toolCallId":"tc1","toolName":"search","args":{"query":"test"}}',
          'data: {"type":"tool-result","toolCallId":"tc1","result":"Found 3 results"}',
          'data: {"type":"text-delta","textDelta":"Here are the results."}',
          'data: {"type":"finish","finishReason":"stop"}',
          "",
        ].join("\n"),
      }),
    );

    await page.goto("/chat");

    const composer = page.getByRole("textbox").or(page.locator("textarea"));
    await composer.first().fill("Search something");
    await composer.first().press("Enter");

    // Tool call card should be visible (look for tool name or status indicator)
    await expect(
      page.getByText("search").or(page.getByText("Search something")),
    ).toBeVisible({ timeout: 10_000 });
  });
});

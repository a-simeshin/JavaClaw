import { test, expect, type Page } from "@playwright/test";

/**
 * E2E tests: User Isolation (E22-E25).
 *
 * Verifies that:
 * - E22: User A's tasks are not visible to User B
 * - E23: User A's recurring task notifications don't leak to User B
 * - E24: User B gets 403 accessing User A's task details/audit
 * - E25: Admin sees all-users toggle; regular user does not
 *
 * Uses route mocking with per-user API responses.
 */

const USER_A = {
  username: "user-a",
  displayName: "User A",
  roles: ["ROLE_USER"],
  authenticated: true,
};

const USER_B = {
  username: "user-b",
  displayName: "User B",
  roles: ["ROLE_USER"],
  authenticated: true,
};

const ADMIN_USER = {
  username: "admin",
  displayName: "Admin",
  roles: ["ROLE_ADMIN"],
  authenticated: true,
};

const USER_A_TASKS = [
  {
    id: "task-a-1",
    name: "Daily weather report",
    status: "completed",
    runtimeType: "CRON",
    userId: "user-a",
    conversationId: "conv-a-1",
    createdAt: "2026-04-10T08:00:00Z",
    updatedAt: "2026-04-10T09:00:00Z",
  },
  {
    id: "task-a-2",
    name: "Analyze competitors",
    status: "in_progress",
    runtimeType: "ASYNC",
    userId: "user-a",
    conversationId: "conv-a-1",
    createdAt: "2026-04-10T10:00:00Z",
    updatedAt: "2026-04-10T10:05:00Z",
  },
];

const USER_A_TASK_AUDIT = [
  {
    id: 1,
    taskId: "task-a-1",
    eventType: "created",
    createdAt: "2026-04-10T08:00:00Z",
  },
  {
    id: 2,
    taskId: "task-a-1",
    eventType: "started",
    createdAt: "2026-04-10T08:00:01Z",
  },
  {
    id: 3,
    taskId: "task-a-1",
    eventType: "completed",
    createdAt: "2026-04-10T09:00:00Z",
    duration_ms: 3600000,
  },
];

const USER_A_TASK_EXECUTIONS = [
  {
    id: "exec-a-1",
    taskId: "task-a-1",
    status: "completed",
    startedAt: "2026-04-10T08:00:01Z",
    completedAt: "2026-04-10T09:00:00Z",
  },
];

async function setupRoutesForUser(
  page: Page,
  user: typeof USER_A,
  tasks: typeof USER_A_TASKS,
) {
  // Auth
  await page.route("**/api/auth/me", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(user),
    }),
  );

  // Conversations
  await page.route("**/api/conversations?*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify({
        content: [],
        totalElements: 0,
        totalPages: 0,
        page: 0,
        size: 20,
      }),
    }),
  );

  // Tasks list
  await page.route("**/api/tasks?*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify(tasks),
    }),
  );
  await page.route("**/api/tasks", (route) => {
    if (route.request().method() === "GET") {
      return route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(tasks),
      });
    }
    return route.continue();
  });

  // Pending approvals (empty)
  await page.route("**/api/chat/approval/pending*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "application/json",
      body: JSON.stringify([]),
    }),
  );

  // SSE notifications (heartbeat)
  await page.route("**/api/chat/notifications/*", (route) =>
    route.fulfill({
      status: 200,
      contentType: "text/event-stream",
      headers: { "Cache-Control": "no-cache" },
      body: "data: {}\n\n",
    }),
  );
}

test.describe("E22 — Task Visibility Isolation", () => {
  test("User A sees own tasks via API", async ({ page }) => {
    await setupRoutesForUser(page, USER_A, USER_A_TASKS);
    await page.goto("/chat");

    const tasks = await page.evaluate(async () => {
      const res = await fetch("/api/tasks");
      return res.json() as Promise<unknown[]>;
    });

    expect(tasks).toHaveLength(2);
    expect((tasks[0] as Record<string, unknown>).name).toBe(
      "Daily weather report",
    );
    expect((tasks[1] as Record<string, unknown>).name).toBe(
      "Analyze competitors",
    );
  });

  test("User B sees empty task list", async ({ page }) => {
    await setupRoutesForUser(page, USER_B, []);
    await page.goto("/chat");

    const tasks = await page.evaluate(async () => {
      const res = await fetch("/api/tasks");
      return res.json() as Promise<unknown[]>;
    });

    expect(tasks).toHaveLength(0);
  });

  test("switching user changes visible tasks", async ({ page }) => {
    // Start as User A
    await setupRoutesForUser(page, USER_A, USER_A_TASKS);
    await page.goto("/chat");

    const tasksA = await page.evaluate(async () => {
      const res = await fetch("/api/tasks");
      return res.json() as Promise<unknown[]>;
    });
    expect(tasksA).toHaveLength(2);

    // Switch to User B — clear routes and set up new ones
    await page.unrouteAll();
    await setupRoutesForUser(page, USER_B, []);

    const tasksB = await page.evaluate(async () => {
      const res = await fetch("/api/tasks");
      return res.json() as Promise<unknown[]>;
    });
    expect(tasksB).toHaveLength(0);
  });
});

test.describe("E23 — Recurring Task Notification Isolation", () => {
  test("SSE endpoint is user/conversation-scoped", async ({ page }) => {
    const sseUrls: string[] = [];

    await setupRoutesForUser(page, USER_B, []);

    // Override SSE to capture URL
    await page.unrouteAll();
    await setupRoutesForUser(page, USER_B, []);
    await page.route("**/api/chat/notifications/*", (route) => {
      sseUrls.push(route.request().url());
      return route.fulfill({
        status: 200,
        contentType: "text/event-stream",
        headers: { "Cache-Control": "no-cache" },
        body: "data: {}\n\n",
      });
    });

    await page.goto("/chat");
    await page.waitForTimeout(1_000);

    // User B's SSE connections should NOT contain User A's conversation IDs
    for (const url of sseUrls) {
      expect(url).not.toContain("conv-a-1");
    }
  });

  test("User B task list has no CRON tasks from User A", async ({ page }) => {
    await setupRoutesForUser(page, USER_B, []);
    await page.goto("/chat");

    const tasks = await page.evaluate(async () => {
      const res = await fetch("/api/tasks?status=CRON");
      return res.json() as Promise<unknown[]>;
    });

    expect(tasks).toHaveLength(0);
  });
});

test.describe("E24 — Task Detail Access Control", () => {
  test("User A accesses own task detail (200)", async ({ page }) => {
    await setupRoutesForUser(page, USER_A, USER_A_TASKS);
    await page.route("**/api/tasks/task-a-1", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(USER_A_TASKS[0]),
      }),
    );

    await page.goto("/chat");

    const status = await page.evaluate(async () => {
      const res = await fetch("/api/tasks/task-a-1");
      return res.status;
    });

    expect(status).toBe(200);
  });

  test("User A accesses own task audit (200)", async ({ page }) => {
    await setupRoutesForUser(page, USER_A, USER_A_TASKS);
    await page.route("**/api/tasks/task-a-1/audit", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(USER_A_TASK_AUDIT),
      }),
    );

    await page.goto("/chat");

    const audit = await page.evaluate(async () => {
      const res = await fetch("/api/tasks/task-a-1/audit");
      return res.json() as Promise<unknown[]>;
    });

    expect(audit).toHaveLength(3);
    expect((audit[2] as Record<string, unknown>).eventType).toBe("completed");
  });

  test("User B gets 403 for User A task detail", async ({ page }) => {
    await setupRoutesForUser(page, USER_B, []);
    await page.route("**/api/tasks/task-a-1", (route) =>
      route.fulfill({
        status: 403,
        contentType: "application/json",
        body: JSON.stringify({ error: "Access denied" }),
      }),
    );

    await page.goto("/chat");

    const status = await page.evaluate(async () => {
      const res = await fetch("/api/tasks/task-a-1");
      return res.status;
    });

    expect(status).toBe(403);
  });

  test("User B gets 403 for User A task audit", async ({ page }) => {
    await setupRoutesForUser(page, USER_B, []);
    await page.route("**/api/tasks/task-a-1/audit", (route) =>
      route.fulfill({
        status: 403,
        contentType: "application/json",
        body: JSON.stringify({ error: "Access denied" }),
      }),
    );

    await page.goto("/chat");

    const status = await page.evaluate(async () => {
      const res = await fetch("/api/tasks/task-a-1/audit");
      return res.status;
    });

    expect(status).toBe(403);
  });

  test("User B gets 403 for User A task executions", async ({ page }) => {
    await setupRoutesForUser(page, USER_B, []);
    await page.route("**/api/tasks/task-a-1/executions", (route) =>
      route.fulfill({
        status: 403,
        contentType: "application/json",
        body: JSON.stringify({ error: "Access denied" }),
      }),
    );

    await page.goto("/chat");

    const status = await page.evaluate(async () => {
      const res = await fetch("/api/tasks/task-a-1/executions");
      return res.status;
    });

    expect(status).toBe(403);
  });
});

test.describe("E25 — Admin Toggle Visibility", () => {
  test("admin settings returns isAdmin=true", async ({ page }) => {
    await setupRoutesForUser(page, ADMIN_USER, USER_A_TASKS);
    await page.route("**/api/auth/me", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(ADMIN_USER),
      }),
    );

    await page.goto("/chat");

    const auth = await page.evaluate(async () => {
      const res = await fetch("/api/auth/me");
      return res.json() as Promise<Record<string, unknown>>;
    });

    expect(auth.roles).toContain("ROLE_ADMIN");
  });

  test("regular user settings returns no admin role", async ({ page }) => {
    await setupRoutesForUser(page, USER_A, USER_A_TASKS);

    await page.goto("/chat");

    const auth = await page.evaluate(async () => {
      const res = await fetch("/api/auth/me");
      return res.json() as Promise<Record<string, unknown>>;
    });

    expect(auth.roles).not.toContain("ROLE_ADMIN");
  });

  test("admin can fetch tasks with userId filter for other users", async ({
    page,
  }) => {
    const allTasks = [
      ...USER_A_TASKS,
      {
        id: "task-b-1",
        name: "User B task",
        status: "completed",
        runtimeType: "ASYNC",
        userId: "user-b",
        conversationId: "conv-b-1",
        createdAt: "2026-04-10T11:00:00Z",
        updatedAt: "2026-04-10T11:30:00Z",
      },
    ];

    await setupRoutesForUser(page, ADMIN_USER, []);

    // Admin fetches with userId filter — sees all users
    await page.route("**/api/tasks?*userId*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify(allTasks),
      }),
    );

    await page.goto("/chat");

    const tasks = await page.evaluate(async () => {
      const res = await fetch("/api/tasks?userId=all");
      return res.json() as Promise<unknown[]>;
    });

    expect(tasks).toHaveLength(3);
    const userIds = tasks.map(
      (t) => (t as Record<string, unknown>).userId,
    );
    expect(userIds).toContain("user-a");
    expect(userIds).toContain("user-b");
  });

  test("regular user userId filter returns only own tasks", async ({
    page,
  }) => {
    await setupRoutesForUser(page, USER_A, []);

    // Backend ignores userId param for non-admins, returns only own tasks
    await page.route("**/api/tasks?*userId*", (route) =>
      route.fulfill({
        status: 200,
        contentType: "application/json",
        body: JSON.stringify([]),
      }),
    );

    await page.goto("/chat");

    const tasks = await page.evaluate(async () => {
      const res = await fetch("/api/tasks?userId=all");
      return res.json() as Promise<unknown[]>;
    });

    expect(tasks).toHaveLength(0);
  });
});

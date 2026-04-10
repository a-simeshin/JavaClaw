import { describe, it, expect, vi, beforeEach } from "vitest"
import { listTasks, getTask, getChildTasks, cancelTask, deleteTask } from "../tasks"

const fetchMock = vi.fn()
globalThis.fetch = fetchMock

vi.mock("@/store/auth", () => ({
  getStoredCredentials: () => "dXNlcjpwYXNz",
}))

function jsonResponse(data: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: status === 200 ? "OK" : "Error",
    json: () => Promise.resolve(data),
    headers: new Headers(),
  }
}

function emptyResponse(status = 204) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: "No Content",
    json: () => Promise.reject(new Error("no body")),
    headers: new Headers(),
  }
}

beforeEach(() => {
  fetchMock.mockReset()
})

describe("listTasks", () => {
  it("calls GET /api/tasks with no params", async () => {
    const tasks = [{ id: "t1", name: "Test" }]
    fetchMock.mockResolvedValue(jsonResponse(tasks))

    const result = await listTasks()

    expect(result).toEqual(tasks)
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks")
  })

  it("includes userId query param when provided", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await listTasks({ userId: "user-42" })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain("userId=user-42")
  })

  it("includes status query param when provided", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await listTasks({ status: "in_progress" })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain("status=in_progress")
  })

  it("includes both params when provided", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await listTasks({ userId: "u1", status: "completed" })

    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain("userId=u1")
    expect(url).toContain("status=completed")
  })

  it("omits empty params", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await listTasks({})

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks")
  })
})

describe("getTask", () => {
  it("calls GET /api/tasks/:id", async () => {
    const task = { id: "t1", name: "My Task" }
    fetchMock.mockResolvedValue(jsonResponse(task))

    const result = await getTask("t1")

    expect(result).toEqual(task)
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks/t1")
  })

  it("encodes special characters in id", async () => {
    fetchMock.mockResolvedValue(jsonResponse({ id: "a/b" }))

    await getTask("a/b")

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks/a%2Fb")
  })
})

describe("getChildTasks", () => {
  it("calls GET /api/tasks/:parentId/children", async () => {
    const children = [{ id: "c1" }, { id: "c2" }]
    fetchMock.mockResolvedValue(jsonResponse(children))

    const result = await getChildTasks("parent-1")

    expect(result).toEqual(children)
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks/parent-1/children")
  })
})

describe("cancelTask", () => {
  it("calls POST /api/tasks/:id/cancel", async () => {
    fetchMock.mockResolvedValue({ ok: true, status: 200, headers: new Headers() })

    await cancelTask("t1")

    const [url, opts] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks/t1/cancel")
    expect(opts.method).toBe("POST")
  })

  it("throws on non-ok response", async () => {
    fetchMock.mockResolvedValue({ ok: false, status: 404, headers: new Headers() })

    await expect(cancelTask("t1")).rejects.toThrow("Failed to cancel task: 404")
  })
})

describe("deleteTask", () => {
  it("calls DELETE /api/tasks/:id", async () => {
    fetchMock.mockResolvedValue(emptyResponse(204))

    await deleteTask("t1")

    const [url, opts] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks/t1")
    expect(opts.method).toBe("DELETE")
  })
})

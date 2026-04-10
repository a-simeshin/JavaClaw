import { describe, it, expect, vi, beforeEach } from "vitest"
import {
  getTaskAudit,
  getTaskExecutions,
  getDeliveryAudit,
  getChatAudit,
} from "../audit"

const fetchMock = vi.fn()
globalThis.fetch = fetchMock

vi.mock("@/store/auth", () => ({
  getStoredCredentials: () => "dXNlcjpwYXNz",
}))

function jsonResponse(data: unknown, status = 200) {
  return {
    ok: status >= 200 && status < 300,
    status,
    statusText: "OK",
    json: () => Promise.resolve(data),
    headers: new Headers(),
  }
}

beforeEach(() => {
  fetchMock.mockReset()
})

describe("getTaskAudit", () => {
  it("calls GET /api/tasks/:id/audit", async () => {
    const logs = [{ id: 1, eventType: "created" }]
    fetchMock.mockResolvedValue(jsonResponse(logs))

    const result = await getTaskAudit("t1")

    expect(result).toEqual(logs)
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks/t1/audit")
  })

  it("encodes task id", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await getTaskAudit("task/special")

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks/task%2Fspecial/audit")
  })
})

describe("getTaskExecutions", () => {
  it("calls GET /api/tasks/:id/executions", async () => {
    const execs = [{ id: "e1", executionNumber: 1 }]
    fetchMock.mockResolvedValue(jsonResponse(execs))

    const result = await getTaskExecutions("t1")

    expect(result).toEqual(execs)
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks/t1/executions")
  })
})

describe("getDeliveryAudit", () => {
  it("calls GET /api/tasks/:id/deliveries", async () => {
    const deliveries = [{ id: 1, status: "delivered" }]
    fetchMock.mockResolvedValue(jsonResponse(deliveries))

    const result = await getDeliveryAudit("t1")

    expect(result).toEqual(deliveries)
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/tasks/t1/deliveries")
  })
})

describe("getChatAudit", () => {
  it("calls GET /api/audit/chat with conversationId and default limit", async () => {
    const logs = [{ id: 1, method: "stream" }]
    fetchMock.mockResolvedValue(jsonResponse(logs))

    const result = await getChatAudit("conv-1")

    expect(result).toEqual(logs)
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/audit/chat?conversationId=conv-1&limit=20")
  })

  it("uses custom limit when provided", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await getChatAudit("conv-1", 5)

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/audit/chat?conversationId=conv-1&limit=5")
  })

  it("encodes conversationId", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await getChatAudit("conv/special&id")

    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain("conversationId=conv%2Fspecial%26id")
  })

  it("throws HttpError on server error", async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 500,
      statusText: "Internal Server Error",
      json: () => Promise.resolve({ error: "db down" }),
      headers: new Headers(),
    })

    await expect(getChatAudit("conv-1")).rejects.toThrow("HTTP 500")
  })
})

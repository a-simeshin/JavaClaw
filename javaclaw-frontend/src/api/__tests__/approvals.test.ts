import { describe, it, expect, vi, beforeEach } from "vitest"
import { getPendingApprovals, respondToApproval } from "../approvals"

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

describe("getPendingApprovals", () => {
  it("calls GET /api/chat/approval/pending with conversationId", async () => {
    const approvals = [{ id: "a1", question: "Buy?" }]
    fetchMock.mockResolvedValue(jsonResponse(approvals))

    const result = await getPendingApprovals("conv-1")

    expect(result).toEqual(approvals)
    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/chat/approval/pending?conversationId=conv-1")
  })

  it("encodes special characters in conversationId", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await getPendingApprovals("conv/special&id")

    const [url] = fetchMock.mock.calls[0]
    expect(url).toContain("conversationId=conv%2Fspecial%26id")
  })

  it("sends auth header", async () => {
    fetchMock.mockResolvedValue(jsonResponse([]))

    await getPendingApprovals("conv-1")

    const [, opts] = fetchMock.mock.calls[0]
    const headers = opts.headers as Headers
    expect(headers.get("Authorization")).toBe("Basic dXNlcjpwYXNz")
  })
})

describe("respondToApproval", () => {
  it("calls POST /api/chat/approval/:id/respond with response body", async () => {
    fetchMock.mockResolvedValue(jsonResponse(undefined, 204))

    await respondToApproval("a1", "Yes, buy it")

    const [url, opts] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/chat/approval/a1/respond")
    expect(opts.method).toBe("POST")
    expect(JSON.parse(opts.body)).toEqual({ response: "Yes, buy it" })
  })

  it("encodes special characters in approvalId", async () => {
    fetchMock.mockResolvedValue(jsonResponse(undefined, 204))

    await respondToApproval("a/b", "ok")

    const [url] = fetchMock.mock.calls[0]
    expect(url).toBe("/api/chat/approval/a%2Fb/respond")
  })

  it("throws HttpError on failure", async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 404,
      statusText: "Not Found",
      json: () => Promise.resolve({ error: "not found" }),
      headers: new Headers(),
    })

    await expect(respondToApproval("a1", "yes")).rejects.toThrow("HTTP 404")
  })
})

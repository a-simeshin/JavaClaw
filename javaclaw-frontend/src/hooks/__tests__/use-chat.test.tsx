import { describe, expect, it } from "vitest"

import { createChatFetch } from "@/api/chat"

describe("createChatFetch", () => {
  it("invokes onUnauthorized for 401 responses", async () => {
    const originalFetch = globalThis.fetch
    let called = false
    globalThis.fetch = async () =>
      new Response(null, { status: 401 }) as Response
    const customFetch = createChatFetch(() => {
      called = true
    })
    await customFetch("/api/chat/send")
    expect(called).toBe(true)
    globalThis.fetch = originalFetch
  })

  it("does not invoke onUnauthorized on 200", async () => {
    const originalFetch = globalThis.fetch
    let called = false
    globalThis.fetch = async () =>
      new Response("ok", { status: 200 }) as Response
    const customFetch = createChatFetch(() => {
      called = true
    })
    await customFetch("/api/chat/send")
    expect(called).toBe(false)
    globalThis.fetch = originalFetch
  })
})

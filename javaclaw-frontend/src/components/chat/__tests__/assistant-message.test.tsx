import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { AssistantMessage } from "@/components/chat/assistant-message"
import "@/test/i18n-test"

describe("AssistantMessage", () => {
  it("renders plain text content", () => {
    render(<AssistantMessage content="Hello world" />)
    expect(screen.getByText("Hello world")).toBeInTheDocument()
  })

  it("renders markdown as formatted html", () => {
    render(<AssistantMessage content="**bold** text" />)
    const bold = screen.getByText("bold")
    expect(bold.tagName.toLowerCase()).toBe("strong")
  })

  it("exposes a copy action", () => {
    render(<AssistantMessage content="payload" />)
    expect(
      screen.getByRole("button", { name: /copy/i }),
    ).toBeInTheDocument()
  })
})

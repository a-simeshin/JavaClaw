import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { TypingIndicator } from "@/components/chat/typing-indicator"
import "@/test/i18n-test"

describe("TypingIndicator", () => {
  it("renders with a polite live region", () => {
    render(<TypingIndicator />)
    const region = screen.getByRole("status")
    expect(region).toHaveAttribute("aria-live", "polite")
  })
})

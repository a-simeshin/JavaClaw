import { render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { UserMessage } from "@/components/chat/user-message"

describe("UserMessage", () => {
  it("renders the content inside a user bubble", () => {
    render(<UserMessage content="ping" />)
    expect(screen.getByText("ping")).toBeInTheDocument()
    expect(
      screen.getByText("ping").closest('[data-role="user"]'),
    ).not.toBeNull()
  })

  it("renders nothing for empty content", () => {
    const { container } = render(<UserMessage content="   " />)
    expect(container.firstChild).toBeNull()
  })
})

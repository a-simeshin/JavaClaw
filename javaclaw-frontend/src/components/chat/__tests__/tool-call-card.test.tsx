import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { ToolCallCard } from "@/components/chat/tool-call-card"
import "@/test/i18n-test"

describe("ToolCallCard", () => {
  it.each([
    ["pending", "Pending"],
    ["running", "Running"],
    ["complete", "Complete"],
    ["error", "Error"],
  ] as const)("renders %s status badge", (status, label) => {
    render(<ToolCallCard name="search" status={status} />)
    expect(screen.getByText(label)).toBeInTheDocument()
  })

  it("expands to show input and output when toggled", () => {
    render(
      <ToolCallCard
        name="search"
        status="complete"
        input={{ query: "hi" }}
        output={{ hits: 1 }}
      />,
    )
    const trigger = screen.getByRole("button", { name: /show details/i })
    expect(trigger).toHaveAttribute("aria-expanded", "false")
    fireEvent.click(trigger)
    expect(trigger).toHaveAttribute("aria-expanded", "true")
    expect(screen.getByText(/"query": "hi"/)).toBeInTheDocument()
    expect(screen.getByText(/"hits": 1/)).toBeInTheDocument()
  })

  it("renders error message in error state", () => {
    render(
      <ToolCallCard
        name="search"
        status="error"
        errorMessage="boom"
      />,
    )
    fireEvent.click(screen.getByRole("button", { name: /show details/i }))
    expect(screen.getByText("boom")).toBeInTheDocument()
  })
})

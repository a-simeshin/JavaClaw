import { render } from "@testing-library/react"
import { describe, expect, it } from "vitest"

import { ConversationBadge } from "@/components/chat/conversation-badge"

describe("ConversationBadge", () => {
  it("renders nothing when all indicators are inactive", () => {
    const { container } = render(<ConversationBadge />)
    expect(container.querySelector('[data-role="conversation-badge"]')).toBeNull()
  })

  it("renders unread count badge", () => {
    const { container } = render(<ConversationBadge unreadCount={5} />)
    const badge = container.querySelector('[data-indicator="unread-count"]')
    expect(badge).toBeInTheDocument()
    expect(badge?.textContent).toBe("5")
  })

  it("caps unread count at 99+", () => {
    const { container } = render(<ConversationBadge unreadCount={150} />)
    const badge = container.querySelector('[data-indicator="unread-count"]')
    expect(badge?.textContent).toBe("99+")
  })

  it("renders pending approval orange dot", () => {
    const { container } = render(<ConversationBadge hasPendingApproval />)
    expect(
      container.querySelector('[data-indicator="pending-approval"]'),
    ).toBeInTheDocument()
  })

  it("renders active task pulsing indicator", () => {
    const { container } = render(<ConversationBadge hasActiveTask />)
    expect(
      container.querySelector('[data-indicator="active-task"]'),
    ).toBeInTheDocument()
  })

  it("renders all indicators simultaneously", () => {
    const { container } = render(
      <ConversationBadge unreadCount={3} hasPendingApproval hasActiveTask />,
    )
    expect(
      container.querySelector('[data-indicator="unread-count"]'),
    ).toBeInTheDocument()
    expect(
      container.querySelector('[data-indicator="pending-approval"]'),
    ).toBeInTheDocument()
    expect(
      container.querySelector('[data-indicator="active-task"]'),
    ).toBeInTheDocument()
  })

  it("does not render unread badge for zero count", () => {
    const { container } = render(<ConversationBadge unreadCount={0} hasActiveTask />)
    expect(
      container.querySelector('[data-indicator="unread-count"]'),
    ).toBeNull()
  })
})

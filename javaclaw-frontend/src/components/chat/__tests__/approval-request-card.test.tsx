import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { ApprovalRequestCard } from "@/components/chat/approval-request-card"
import "@/test/i18n-test"

const futureTimeout = new Date(Date.now() + 60_000).toISOString()
const pastTimeout = new Date(Date.now() - 5_000).toISOString()

describe("ApprovalRequestCard", () => {
  it("renders question text and task name", () => {
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Buy tickets"
        question="Found for 9500. Buy?"
        timeoutAt={futureTimeout}
      />,
    )
    expect(screen.getByText("Buy tickets")).toBeInTheDocument()
    expect(screen.getByText("Found for 9500. Buy?")).toBeInTheDocument()
    expect(screen.getByText("Awaiting input")).toBeInTheDocument()
  })

  it("shows approve and deny buttons when pending", () => {
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        onApprove={vi.fn()}
        onDeny={vi.fn()}
      />,
    )
    expect(screen.getByText("Approve")).toBeInTheDocument()
    expect(screen.getByText("Deny")).toBeInTheDocument()
  })

  it("calls onApprove when approve button clicked", () => {
    const onApprove = vi.fn()
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        onApprove={onApprove}
      />,
    )
    fireEvent.click(screen.getByText("Approve"))
    expect(onApprove).toHaveBeenCalledOnce()
  })

  it("calls onDeny when deny button clicked", () => {
    const onDeny = vi.fn()
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        onDeny={onDeny}
      />,
    )
    fireEvent.click(screen.getByText("Deny"))
    expect(onDeny).toHaveBeenCalledOnce()
  })

  it("shows approved pill when resolved=approved", () => {
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        resolved="approved"
      />,
    )
    expect(screen.getByText("Approved")).toBeInTheDocument()
    expect(screen.queryByText("Approve")).not.toBeInTheDocument()
    expect(screen.queryByText("Deny")).not.toBeInTheDocument()
  })

  it("shows denied pill when resolved=denied", () => {
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        resolved="denied"
      />,
    )
    expect(screen.getByText("Denied")).toBeInTheDocument()
  })

  it("shows expired pill when resolved=timeout", () => {
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={pastTimeout}
        resolved="timeout"
      />,
    )
    expect(screen.getByText("Expired")).toBeInTheDocument()
  })

  it("shows expired state when timeoutAt is in the past", () => {
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={pastTimeout}
      />,
    )
    expect(screen.getByText("Expired")).toBeInTheDocument()
  })

  it("renders countdown bar with progressbar role when pending", () => {
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
      />,
    )
    expect(screen.getByRole("progressbar")).toBeInTheDocument()
    expect(screen.getByText("Time remaining")).toBeInTheDocument()
  })

  it("hides countdown bar when resolved", () => {
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        resolved="approved"
      />,
    )
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument()
  })

  it("sends quick reply via onRespond and Enter key", () => {
    const onRespond = vi.fn()
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        onRespond={onRespond}
      />,
    )
    const input = screen.getByPlaceholderText("Type a custom response...")
    fireEvent.change(input, { target: { value: "Yes, economy only" } })
    fireEvent.keyDown(input, { key: "Enter" })
    expect(onRespond).toHaveBeenCalledWith("Yes, economy only")
  })

  it("sends quick reply via Send button", () => {
    const onRespond = vi.fn()
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        onRespond={onRespond}
      />,
    )
    const input = screen.getByPlaceholderText("Type a custom response...")
    fireEvent.change(input, { target: { value: "Go ahead" } })
    fireEvent.click(screen.getByText("Send"))
    expect(onRespond).toHaveBeenCalledWith("Go ahead")
  })

  it("does not send empty quick reply", () => {
    const onRespond = vi.fn()
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        onRespond={onRespond}
      />,
    )
    const input = screen.getByPlaceholderText("Type a custom response...")
    fireEvent.keyDown(input, { key: "Enter" })
    expect(onRespond).not.toHaveBeenCalled()
  })

  it("calls onShowAudit when audit link clicked", () => {
    const onShowAudit = vi.fn()
    render(
      <ApprovalRequestCard
        approvalId="a42"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        onShowAudit={onShowAudit}
      />,
    )
    fireEvent.click(screen.getByText("Show audit"))
    expect(onShowAudit).toHaveBeenCalledWith("a42")
  })

  it("renders data-role and data-status attributes", () => {
    const { container } = render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
      />,
    )
    expect(
      container.querySelector('[data-role="approval-request"]'),
    ).toBeInTheDocument()
    expect(
      container.querySelector('[data-status="pending"]'),
    ).toBeInTheDocument()
  })

  it("disables buttons when isSubmitting", () => {
    render(
      <ApprovalRequestCard
        approvalId="a1"
        taskName="Task"
        question="Proceed?"
        timeoutAt={futureTimeout}
        isSubmitting
        onApprove={vi.fn()}
        onDeny={vi.fn()}
      />,
    )
    expect(screen.getByText("Approve").closest("button")).toBeDisabled()
    expect(screen.getByText("Deny").closest("button")).toBeDisabled()
  })
})

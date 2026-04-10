import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { TaskNotificationMessage } from "@/components/chat/task-notification-message"
import "@/test/i18n-test"

describe("TaskNotificationMessage", () => {
  it.each([
    ["completed", "Completed"],
    ["failed", "Failed"],
    ["cancelled", "Cancelled"],
    ["in_progress", "In progress"],
    ["awaiting_input", "Awaiting input"],
    ["todo", "Scheduled"],
  ] as const)("renders %s status badge", (status, label) => {
    render(
      <TaskNotificationMessage taskId="t1" taskName="Test task" status={status} />,
    )
    expect(screen.getByText(label)).toBeInTheDocument()
    expect(screen.getByText("Test task")).toBeInTheDocument()
  })

  it("expands to show message and duration when toggled", () => {
    render(
      <TaskNotificationMessage
        taskId="t1"
        taskName="Reminder"
        status="completed"
        message="Time to drink water!"
        durationMs={3500}
      />,
    )
    const trigger = screen.getByRole("button", { name: /show task details/i })
    expect(trigger).toHaveAttribute("aria-expanded", "false")
    fireEvent.click(trigger)
    expect(trigger).toHaveAttribute("aria-expanded", "true")
    expect(screen.getByText("Time to drink water!")).toBeInTheDocument()
    expect(screen.getByText(/4s/)).toBeInTheDocument()
  })

  it("calls onShowAudit when audit button is clicked", () => {
    const onShowAudit = vi.fn()
    render(
      <TaskNotificationMessage
        taskId="t42"
        taskName="Task"
        status="completed"
        onShowAudit={onShowAudit}
      />,
    )
    fireEvent.click(screen.getByRole("button", { name: /show task details/i }))
    fireEvent.click(screen.getByText("Show audit"))
    expect(onShowAudit).toHaveBeenCalledWith("t42")
  })

  it("renders data-status attribute for styling hooks", () => {
    const { container } = render(
      <TaskNotificationMessage taskId="t1" taskName="Task" status="failed" />,
    )
    expect(
      container.querySelector('[data-status="failed"]'),
    ).toBeInTheDocument()
  })

  it("formats minutes correctly in duration", () => {
    render(
      <TaskNotificationMessage
        taskId="t1"
        taskName="Long task"
        status="completed"
        message="Done"
        durationMs={125000}
      />,
    )
    fireEvent.click(screen.getByRole("button", { name: /show task details/i }))
    expect(screen.getByText(/2m 5s/)).toBeInTheDocument()
  })
})

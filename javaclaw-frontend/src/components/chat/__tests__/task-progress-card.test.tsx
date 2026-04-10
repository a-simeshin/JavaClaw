import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { TaskProgressCard } from "@/components/chat/task-progress-card"
import "@/test/i18n-test"

describe("TaskProgressCard", () => {
  it("renders task name and in_progress status", () => {
    render(
      <TaskProgressCard taskId="t1" taskName="Analyzing competitors" />,
    )
    expect(screen.getByText("Analyzing competitors")).toBeInTheDocument()
    expect(screen.getByText("In progress")).toBeInTheDocument()
  })

  it("renders determinate progress bar with percent", () => {
    render(
      <TaskProgressCard
        taskId="t1"
        taskName="Task"
        progressPercent={40}
        progressText="2 of 5 processed"
      />,
    )
    const bar = screen.getByRole("progressbar")
    expect(bar).toHaveAttribute("aria-valuenow", "40")
    expect(screen.getByText("2 of 5 processed")).toBeInTheDocument()
  })

  it("renders indeterminate progress bar when no percent", () => {
    render(
      <TaskProgressCard taskId="t1" taskName="Task" />,
    )
    const bar = screen.getByRole("progressbar")
    expect(bar).not.toHaveAttribute("aria-valuenow")
  })

  it("calls onCancel when cancel button is clicked", () => {
    const onCancel = vi.fn()
    render(
      <TaskProgressCard
        taskId="t42"
        taskName="Task"
        onCancel={onCancel}
      />,
    )
    fireEvent.click(screen.getByRole("button", { name: /cancel task/i }))
    expect(onCancel).toHaveBeenCalledWith("t42")
  })

  it("clamps percent to 0-100 range", () => {
    render(
      <TaskProgressCard taskId="t1" taskName="Task" progressPercent={150} />,
    )
    const bar = screen.getByRole("progressbar")
    expect(bar).toHaveAttribute("aria-valuenow", "100")
  })

  it("renders data-role attribute for styling hooks", () => {
    const { container } = render(
      <TaskProgressCard taskId="t1" taskName="Task" />,
    )
    expect(
      container.querySelector('[data-role="task-progress"]'),
    ).toBeInTheDocument()
  })
})

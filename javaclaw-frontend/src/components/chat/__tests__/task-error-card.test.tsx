import { fireEvent, render, screen } from "@testing-library/react"
import { describe, expect, it, vi } from "vitest"

import { TaskErrorCard } from "@/components/chat/task-error-card"
import "@/test/i18n-test"

describe("TaskErrorCard", () => {
  it("renders error message and task name", () => {
    render(
      <TaskErrorCard
        taskId="t1"
        taskName="Broken task"
        errorMessage="Connection timeout"
      />,
    )
    expect(screen.getByText("Broken task")).toBeInTheDocument()
    expect(screen.getByText("Connection timeout")).toBeInTheDocument()
    expect(screen.getByText("Failed")).toBeInTheDocument()
  })

  it("expands to show stacktrace and LLM request", () => {
    render(
      <TaskErrorCard
        taskId="t1"
        taskName="Task"
        errorMessage="Error"
        errorTrace="java.lang.NullPointerException\n  at Foo.bar(Foo.java:42)"
        llmRequest="System: You are a helpful assistant"
      />,
    )
    fireEvent.click(screen.getByRole("button", { name: /show task details/i }))
    expect(screen.getByText(/NullPointerException/)).toBeInTheDocument()
    expect(screen.getByText(/helpful assistant/)).toBeInTheDocument()
  })

  it("calls onRetry when retry button is clicked", () => {
    const onRetry = vi.fn()
    render(
      <TaskErrorCard
        taskId="t42"
        taskName="Task"
        errorMessage="Error"
        onRetry={onRetry}
      />,
    )
    fireEvent.click(screen.getByText("Retry"))
    expect(onRetry).toHaveBeenCalledWith("t42")
  })

  it("calls onShowAudit when audit link is clicked", () => {
    const onShowAudit = vi.fn()
    render(
      <TaskErrorCard
        taskId="t42"
        taskName="Task"
        errorMessage="Error"
        onShowAudit={onShowAudit}
      />,
    )
    fireEvent.click(screen.getByText("Show audit"))
    expect(onShowAudit).toHaveBeenCalledWith("t42")
  })

  it("renders error border styling", () => {
    const { container } = render(
      <TaskErrorCard taskId="t1" taskName="Task" errorMessage="Error" />,
    )
    expect(
      container.querySelector('[data-role="task-error"]'),
    ).toBeInTheDocument()
  })
})

import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { describe, expect, it, vi, beforeEach } from "vitest"

import type { TaskDto } from "@/api/tasks"
import "@/test/i18n-test"

// Mock API modules
vi.mock("@/api/tasks", () => ({
  listTasks: vi.fn(),
  getTask: vi.fn(),
  getChildTasks: vi.fn(),
  cancelTask: vi.fn(),
  deleteTask: vi.fn(),
}))

vi.mock("@/api/audit", () => ({
  getTaskAudit: vi.fn(),
  getTaskExecutions: vi.fn(),
  getDeliveryAudit: vi.fn(),
}))

import { listTasks, getTask, getChildTasks } from "@/api/tasks"
import { getTaskAudit, getTaskExecutions, getDeliveryAudit } from "@/api/audit"
import { TasksPage } from "@/components/tasks/tasks-page"

const mockListTasks = listTasks as ReturnType<typeof vi.fn>
const mockGetTask = getTask as ReturnType<typeof vi.fn>
const mockGetChildTasks = getChildTasks as ReturnType<typeof vi.fn>
const mockGetTaskAudit = getTaskAudit as ReturnType<typeof vi.fn>
const mockGetTaskExecutions = getTaskExecutions as ReturnType<typeof vi.fn>
const mockGetDeliveryAudit = getDeliveryAudit as ReturnType<typeof vi.fn>

function makeTask(overrides: Partial<TaskDto> = {}): TaskDto {
  return {
    id: "task-1",
    name: "My test task",
    description: null,
    status: "completed",
    feedback: null,
    conversationId: "conv-1",
    parentTaskId: null,
    notifyPolicy: "done_only",
    runtimeType: "async",
    timeoutSeconds: null,
    carryOverContext: null,
    userId: "user-1",
    createdAt: "2026-04-10T10:00:00Z",
    updatedAt: "2026-04-10T10:05:00Z",
    failedAt: null,
    cancelledAt: null,
    ...overrides,
  }
}

describe("TasksPage", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListTasks.mockResolvedValue([])
    mockGetChildTasks.mockResolvedValue([])
  })

  it("renders task list panel", async () => {
    mockListTasks.mockResolvedValue([makeTask()])
    render(<TasksPage />)
    await waitFor(() => {
      expect(screen.getByText("My test task")).toBeInTheDocument()
    })
    expect(screen.getByRole("tablist")).toBeInTheDocument()
  })

  it("clicking a task opens the detail dialog", async () => {
    const task = makeTask({ id: "task-42", name: "Detailed task" })
    mockListTasks.mockResolvedValue([task])
    mockGetTask.mockResolvedValue(task)
    mockGetTaskAudit.mockResolvedValue([])
    mockGetTaskExecutions.mockResolvedValue([])
    mockGetDeliveryAudit.mockResolvedValue([])

    render(<TasksPage />)

    await waitFor(() => {
      expect(screen.getByText("Detailed task")).toBeInTheDocument()
    })

    // Click the task row
    fireEvent.click(screen.getByText("Detailed task"))

    // Dialog should open and show the task name after loading
    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument()
    })
    // Verify getTask was called with the correct taskId
    expect(mockGetTask).toHaveBeenCalledWith("task-42")
  })

  it("closing the dialog resets selected task", async () => {
    const task = makeTask({ id: "task-99", name: "Closeable task" })
    mockListTasks.mockResolvedValue([task])
    mockGetTask.mockResolvedValue(task)
    mockGetTaskAudit.mockResolvedValue([])
    mockGetTaskExecutions.mockResolvedValue([])
    mockGetDeliveryAudit.mockResolvedValue([])

    render(<TasksPage />)

    await waitFor(() => {
      expect(screen.getByText("Closeable task")).toBeInTheDocument()
    })

    // Open dialog
    fireEvent.click(screen.getByText("Closeable task"))
    await waitFor(() => {
      expect(screen.getByRole("dialog")).toBeInTheDocument()
    })

    // Close via the close button (Radix Dialog renders an X button)
    const closeButton = screen.getByRole("dialog").querySelector("button[aria-label]")
    if (closeButton) {
      fireEvent.click(closeButton)
    } else {
      // Fallback: press Escape
      fireEvent.keyDown(screen.getByRole("dialog"), { key: "Escape" })
    }

    await waitFor(() => {
      expect(screen.queryByRole("dialog")).not.toBeInTheDocument()
    })
  })

  it("renders data-role attribute on task list panel", async () => {
    mockListTasks.mockResolvedValue([])
    const { container } = render(<TasksPage />)
    await waitFor(() => {
      expect(container.querySelector("[data-role='task-list-panel']")).toBeTruthy()
    })
  })
})

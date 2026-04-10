import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { describe, expect, it, vi, beforeEach } from "vitest"

import type { TaskDto } from "@/api/tasks"
import "@/test/i18n-test"

// Mock API module
vi.mock("@/api/tasks", () => ({
  listTasks: vi.fn(),
  getChildTasks: vi.fn(),
  cancelTask: vi.fn(),
  deleteTask: vi.fn(),
}))

import { listTasks, getChildTasks, cancelTask, deleteTask } from "@/api/tasks"
import { TaskListPanel } from "@/components/tasks/task-list-panel"

const mockListTasks = listTasks as ReturnType<typeof vi.fn>
const mockGetChildTasks = getChildTasks as ReturnType<typeof vi.fn>
const mockCancelTask = cancelTask as ReturnType<typeof vi.fn>
const mockDeleteTask = deleteTask as ReturnType<typeof vi.fn>

function makeTask(overrides: Partial<TaskDto> = {}): TaskDto {
  return {
    id: "task-1",
    name: "Test task",
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

describe("TaskListPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks()
    mockListTasks.mockResolvedValue([])
  })

  it("renders tabs for all filter options", async () => {
    render(<TaskListPanel />)
    await waitFor(() => expect(mockListTasks).toHaveBeenCalled())

    const tablist = screen.getByRole("tablist")
    expect(tablist).toBeInTheDocument()

    for (const label of ["All", "Active", "Recurring", "Completed", "Failed"]) {
      expect(screen.getByRole("tab", { name: label })).toBeInTheDocument()
    }
  })

  it("renders empty state when no tasks", async () => {
    mockListTasks.mockResolvedValue([])
    render(<TaskListPanel />)

    await waitFor(() => {
      expect(screen.getByText("No tasks")).toBeInTheDocument()
    })
  })

  it("renders task list with name and status badge", async () => {
    mockListTasks.mockResolvedValue([
      makeTask({ id: "t1", name: "Deploy service", status: "completed" }),
      makeTask({ id: "t2", name: "Check logs", status: "in_progress" }),
    ])
    render(<TaskListPanel />)

    await waitFor(() => {
      expect(screen.getByText("Deploy service")).toBeInTheDocument()
      expect(screen.getByText("Check logs")).toBeInTheDocument()
    })
    // Status badges appear (also "Completed" appears as tab label, so use getAllByText)
    expect(screen.getAllByText("Completed").length).toBeGreaterThanOrEqual(2) // tab + badge
    expect(screen.getByText("In progress")).toBeInTheDocument()
  })

  it("filters by active tab when clicked", async () => {
    mockListTasks.mockResolvedValue([])
    render(<TaskListPanel />)

    await waitFor(() => expect(mockListTasks).toHaveBeenCalledTimes(1))

    const activeTab = screen.getByRole("tab", { name: "Active" })
    fireEvent.click(activeTab)

    await waitFor(() =>
      expect(mockListTasks).toHaveBeenCalledWith({
        userId: undefined,
        status: "in_progress",
      }),
    )
  })

  it("filters by completed tab", async () => {
    mockListTasks.mockResolvedValue([])
    render(<TaskListPanel />)

    await waitFor(() => expect(mockListTasks).toHaveBeenCalledTimes(1))

    fireEvent.click(screen.getByRole("tab", { name: "Completed" }))

    await waitFor(() =>
      expect(mockListTasks).toHaveBeenCalledWith({
        userId: undefined,
        status: "completed",
      }),
    )
  })

  it("calls onSelectTask when task row is clicked", async () => {
    const onSelect = vi.fn()
    mockListTasks.mockResolvedValue([makeTask({ id: "t99", name: "My task" })])
    render(<TaskListPanel onSelectTask={onSelect} />)

    await waitFor(() => {
      expect(screen.getByText("My task")).toBeInTheDocument()
    })

    fireEvent.click(screen.getByText("My task"))
    expect(onSelect).toHaveBeenCalledWith("t99")
  })

  it("shows cancel button for active tasks on hover", async () => {
    mockListTasks.mockResolvedValue([
      makeTask({ id: "t1", name: "Running task", status: "in_progress" }),
    ])
    mockCancelTask.mockResolvedValue(undefined)
    render(<TaskListPanel />)

    await waitFor(() => {
      expect(screen.getByText("Running task")).toBeInTheDocument()
    })

    const cancelBtn = screen.getByLabelText("Cancel")
    fireEvent.click(cancelBtn)

    await waitFor(() => {
      expect(mockCancelTask).toHaveBeenCalledWith("t1")
    })
  })

  it("shows delete button for terminal tasks", async () => {
    mockListTasks.mockResolvedValue([
      makeTask({ id: "t1", name: "Done task", status: "completed" }),
    ])
    mockDeleteTask.mockResolvedValue(undefined)
    render(<TaskListPanel />)

    await waitFor(() => {
      expect(screen.getByText("Done task")).toBeInTheDocument()
    })

    const deleteBtn = screen.getByLabelText("Delete")
    fireEvent.click(deleteBtn)

    await waitFor(() => {
      expect(mockDeleteTask).toHaveBeenCalledWith("t1")
    })
  })

  it("expands parent task to show children", async () => {
    mockListTasks.mockResolvedValue([
      makeTask({ id: "parent-1", name: "Parent task", status: "completed" }),
    ])
    mockGetChildTasks.mockResolvedValue([
      makeTask({
        id: "child-1",
        name: "Child task 1",
        status: "completed",
        parentTaskId: "parent-1",
      }),
      makeTask({
        id: "child-2",
        name: "Child task 2",
        status: "failed",
        parentTaskId: "parent-1",
      }),
    ])
    render(<TaskListPanel />)

    await waitFor(() => {
      expect(screen.getByText("Parent task")).toBeInTheDocument()
    })

    const expandBtn = screen.getByLabelText("Toggle children")
    fireEvent.click(expandBtn)

    await waitFor(() => {
      expect(screen.getByText("Child task 1")).toBeInTheDocument()
      expect(screen.getByText("Child task 2")).toBeInTheDocument()
    })
    expect(mockGetChildTasks).toHaveBeenCalledWith("parent-1")
  })

  it("shows cron badge for recurring tasks", async () => {
    mockListTasks.mockResolvedValue([
      makeTask({ id: "t1", name: "Daily weather", runtimeType: "cron", status: "completed" }),
    ])
    render(<TaskListPanel />)

    await waitFor(() => {
      expect(screen.getByText("Daily weather")).toBeInTheDocument()
      expect(screen.getByText("Cron")).toBeInTheDocument()
    })
  })

  it("refresh button reloads tasks", async () => {
    mockListTasks.mockResolvedValue([])
    render(<TaskListPanel />)

    await waitFor(() => expect(mockListTasks).toHaveBeenCalledTimes(1))

    fireEvent.click(screen.getByLabelText("Refresh"))

    await waitFor(() => expect(mockListTasks).toHaveBeenCalledTimes(2))
  })

  it("renders data-role attribute for styling hooks", async () => {
    mockListTasks.mockResolvedValue([])
    const { container } = render(<TaskListPanel />)

    await waitFor(() => expect(mockListTasks).toHaveBeenCalled())

    expect(
      container.querySelector('[data-role="task-list-panel"]'),
    ).toBeInTheDocument()
  })

  it("shows error state when API fails", async () => {
    mockListTasks.mockRejectedValue(new Error("Network error"))
    render(<TaskListPanel />)

    await waitFor(() => {
      expect(screen.getByText("Network error")).toBeInTheDocument()
    })
  })

  it("passes userId filter to API", async () => {
    mockListTasks.mockResolvedValue([])
    render(<TaskListPanel userId="user-42" />)

    await waitFor(() =>
      expect(mockListTasks).toHaveBeenCalledWith({
        userId: "user-42",
        status: undefined,
      }),
    )
  })

  it("hides child tasks from top-level list", async () => {
    mockListTasks.mockResolvedValue([
      makeTask({ id: "parent", name: "Parent", status: "completed" }),
      makeTask({ id: "child", name: "Child", status: "completed", parentTaskId: "parent" }),
    ])
    render(<TaskListPanel />)

    await waitFor(() => {
      expect(screen.getByText("Parent")).toBeInTheDocument()
    })
    // Child should not appear in the top-level list
    expect(screen.queryByText("Child")).not.toBeInTheDocument()
  })
})

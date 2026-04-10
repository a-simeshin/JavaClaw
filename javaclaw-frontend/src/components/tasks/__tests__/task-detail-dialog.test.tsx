import { fireEvent, render, screen, waitFor } from "@testing-library/react"
import { describe, expect, it, vi, beforeEach } from "vitest"

import type { TaskDto } from "@/api/tasks"
import type {
  TaskAuditLogDto,
  TaskExecutionDto,
  DeliveryAuditLogDto,
} from "@/api/audit"
import "@/test/i18n-test"

// Mock API modules
vi.mock("@/api/tasks", () => ({
  getTask: vi.fn(),
  listTasks: vi.fn(),
  getChildTasks: vi.fn(),
  cancelTask: vi.fn(),
  deleteTask: vi.fn(),
}))

vi.mock("@/api/audit", () => ({
  getTaskAudit: vi.fn(),
  getTaskExecutions: vi.fn(),
  getDeliveryAudit: vi.fn(),
}))

import { getTask } from "@/api/tasks"
import { getTaskAudit, getTaskExecutions, getDeliveryAudit } from "@/api/audit"
import { TaskDetailDialog } from "@/components/tasks/task-detail-dialog"

const mockGetTask = getTask as ReturnType<typeof vi.fn>
const mockGetTaskAudit = getTaskAudit as ReturnType<typeof vi.fn>
const mockGetTaskExecutions = getTaskExecutions as ReturnType<typeof vi.fn>
const mockGetDeliveryAudit = getDeliveryAudit as ReturnType<typeof vi.fn>

function makeTask(overrides: Partial<TaskDto> = {}): TaskDto {
  return {
    id: "task-1",
    name: "Analyze competitors",
    description: "Full competitor analysis",
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

function makeAuditLog(overrides: Partial<TaskAuditLogDto> = {}): TaskAuditLogDto {
  return {
    id: 1,
    taskId: "task-1",
    executionId: "exec-1",
    eventType: "created",
    createdAt: "2026-04-10T10:00:00Z",
    systemPrompt: null,
    userPrompt: null,
    toolName: null,
    toolArgs: null,
    toolResult: null,
    toolDurationMs: null,
    llmRequest: null,
    llmResponse: null,
    tokenUsage: null,
    errorMessage: null,
    errorTrace: null,
    durationMs: null,
    metadata: null,
    ...overrides,
  }
}

function makeExecution(overrides: Partial<TaskExecutionDto> = {}): TaskExecutionDto {
  return {
    id: "exec-1",
    taskId: "task-1",
    executionNumber: 1,
    status: "completed",
    systemPrompt: "You are a helpful assistant",
    userPrompt: "Analyze competitors",
    llmResponse: "Here is the analysis...",
    errorMessage: null,
    errorTrace: null,
    startedAt: "2026-04-10T10:00:00Z",
    completedAt: "2026-04-10T10:05:00Z",
    durationMs: 300000,
    ...overrides,
  }
}

function makeDelivery(overrides: Partial<DeliveryAuditLogDto> = {}): DeliveryAuditLogDto {
  return {
    id: 1,
    taskId: "task-1",
    conversationId: "conv-1",
    channelName: "WebChatChannel",
    message: "Task completed",
    status: "delivered",
    attempts: 1,
    errorMessage: null,
    durationMs: 50,
    createdAt: "2026-04-10T10:05:01Z",
    ...overrides,
  }
}

function setupMocks({
  task = makeTask(),
  auditLogs = [makeAuditLog()],
  executions = [makeExecution()],
  deliveries = [makeDelivery()],
}: {
  task?: TaskDto
  auditLogs?: TaskAuditLogDto[]
  executions?: TaskExecutionDto[]
  deliveries?: DeliveryAuditLogDto[]
} = {}) {
  mockGetTask.mockResolvedValue(task)
  mockGetTaskAudit.mockResolvedValue(auditLogs)
  mockGetTaskExecutions.mockResolvedValue(executions)
  mockGetDeliveryAudit.mockResolvedValue(deliveries)
}

function renderDialog(taskId: string | null = "task-1", open = true) {
  const onOpenChange = vi.fn()
  render(
    <TaskDetailDialog taskId={taskId} open={open} onOpenChange={onOpenChange} />
  )
  return { onOpenChange }
}

describe("TaskDetailDialog", () => {
  beforeEach(() => {
    vi.clearAllMocks()
  })

  it("renders task name and status after loading", async () => {
    setupMocks()
    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("Analyze competitors")).toBeInTheDocument()
    })
    expect(screen.getByText("completed")).toBeInTheDocument()
    expect(screen.getByText("async")).toBeInTheDocument()
  })

  it("renders all 4 tabs", async () => {
    setupMocks()
    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("Analyze competitors")).toBeInTheDocument()
    })

    const tabs = screen.getAllByRole("tab")
    expect(tabs).toHaveLength(4)
    expect(screen.getByRole("tab", { name: /Timeline/i })).toBeInTheDocument()
    expect(screen.getByRole("tab", { name: /Model request/i })).toBeInTheDocument()
    expect(screen.getByRole("tab", { name: /Tools/i })).toBeInTheDocument()
    expect(screen.getByRole("tab", { name: /Deliveries/i })).toBeInTheDocument()
  })

  it("shows loading spinner while fetching", () => {
    mockGetTask.mockReturnValue(new Promise(() => {})) // never resolves
    mockGetTaskAudit.mockReturnValue(new Promise(() => {}))
    mockGetTaskExecutions.mockReturnValue(new Promise(() => {}))
    mockGetDeliveryAudit.mockReturnValue(new Promise(() => {}))

    renderDialog()

    expect(screen.getByRole("dialog")).toBeInTheDocument()
  })

  it("shows error state when API fails", async () => {
    mockGetTask.mockRejectedValue(new Error("Network error"))
    mockGetTaskAudit.mockRejectedValue(new Error("Network error"))
    mockGetTaskExecutions.mockRejectedValue(new Error("Network error"))
    mockGetDeliveryAudit.mockRejectedValue(new Error("Network error"))

    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("Network error")).toBeInTheDocument()
    })
  })

  it("displays timeline events with event types", async () => {
    setupMocks({
      auditLogs: [
        makeAuditLog({ id: 1, eventType: "created", durationMs: null }),
        makeAuditLog({ id: 2, eventType: "started", durationMs: 100 }),
        makeAuditLog({ id: 3, eventType: "completed", durationMs: 5000 }),
      ],
    })

    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("created")).toBeInTheDocument()
    })
    expect(screen.getByText("started")).toBeInTheDocument()
    // "completed" appears in both status badge and timeline — use getAllByText
    expect(screen.getAllByText("completed").length).toBeGreaterThanOrEqual(2)
    expect(screen.getByText("5.0s")).toBeInTheDocument()
  })

  it("shows no events message when timeline is empty", async () => {
    setupMocks({ auditLogs: [] })
    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("No events recorded")).toBeInTheDocument()
    })
  })

  it("expands timeline event to show tool details", async () => {
    setupMocks({
      auditLogs: [
        makeAuditLog({
          id: 1,
          eventType: "tool_call",
          toolName: "getWeather",
          toolArgs: '{"city":"Moscow"}',
          toolResult: '{"temp":15}',
          toolDurationMs: 200,
        }),
      ],
    })

    renderDialog()

    await waitFor(() => {
      // toolName summary shows as "Tool: getWeather" in collapsed state
      expect(screen.getByText(/getWeather/)).toBeInTheDocument()
    })

    // Initially args/result not visible (collapsed)
    expect(screen.queryByText('{"city":"Moscow"}')).not.toBeInTheDocument()

    // Click expand
    const expandBtn = screen.getByLabelText("expand")
    fireEvent.click(expandBtn)

    expect(screen.getByText('{"city":"Moscow"}')).toBeInTheDocument()
    expect(screen.getByText('{"temp":15}')).toBeInTheDocument()
  })

  it("expands timeline event to show error details", async () => {
    setupMocks({
      auditLogs: [
        makeAuditLog({
          id: 1,
          eventType: "failed",
          errorMessage: "Connection refused",
          errorTrace: "java.net.ConnectException\n  at ...",
        }),
      ],
    })

    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("failed")).toBeInTheDocument()
    })

    // Error message summary visible
    expect(screen.getByText("Connection refused")).toBeInTheDocument()

    // Expand for stacktrace
    const expandBtn = screen.getByLabelText("expand")
    fireEvent.click(expandBtn)

    expect(screen.getByText(/java\.net\.ConnectException/)).toBeInTheDocument()
  })

  it("switches to model tab and shows executions", async () => {
    setupMocks({
      executions: [
        makeExecution({
          id: "exec-1",
          executionNumber: 1,
          status: "completed",
          durationMs: 300000,
          systemPrompt: "You are a helpful assistant",
          userPrompt: "Analyze competitors",
          llmResponse: "Here is the analysis...",
        }),
      ],
    })

    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("Analyze competitors")).toBeInTheDocument()
    })

    // Switch to model tab
    fireEvent.click(screen.getByRole("tab", { name: /Model request/i }))

    expect(screen.getByText("#1")).toBeInTheDocument()
    expect(screen.getByText("5m 0s")).toBeInTheDocument()

    // Click model request button
    const requestButtons = screen.getAllByText("Model request")
    const inTabButton = requestButtons.find(
      (el) => el.tagName.toLowerCase() === "button" && el.getAttribute("role") !== "tab"
    )
    fireEvent.click(inTabButton!)

    expect(screen.getByText("You are a helpful assistant")).toBeInTheDocument()
  })

  it("switches to tools tab and shows tool calls", async () => {
    setupMocks({
      auditLogs: [
        makeAuditLog({ id: 1, eventType: "created" }),
        makeAuditLog({
          id: 2,
          eventType: "tool_call",
          toolName: "searchWeb",
          toolArgs: '{"query":"test"}',
          toolResult: '{"results":[]}',
          toolDurationMs: 1500,
        }),
      ],
    })

    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("Analyze competitors")).toBeInTheDocument()
    })

    // Switch to tools tab
    fireEvent.click(screen.getByRole("tab", { name: /Tools/i }))

    expect(screen.getByText("searchWeb")).toBeInTheDocument()
    expect(screen.getByText("1.5s")).toBeInTheDocument()

    // Expand tool call
    fireEvent.click(screen.getByText("searchWeb"))

    expect(screen.getByText('{"query":"test"}')).toBeInTheDocument()
    expect(screen.getByText('{"results":[]}')).toBeInTheDocument()
  })

  it("shows empty state in tools tab when no tool calls", async () => {
    setupMocks({
      auditLogs: [makeAuditLog({ id: 1, eventType: "created" })],
    })

    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("Analyze competitors")).toBeInTheDocument()
    })

    fireEvent.click(screen.getByRole("tab", { name: /Tools/i }))

    expect(screen.getByText("No events recorded")).toBeInTheDocument()
  })

  it("switches to deliveries tab and shows delivery history", async () => {
    setupMocks({
      deliveries: [
        makeDelivery({
          id: 1,
          channelName: "WebChatChannel",
          status: "delivered",
          attempts: 1,
          durationMs: 50,
        }),
        makeDelivery({
          id: 2,
          channelName: "TelegramChannel",
          status: "failed",
          attempts: 3,
          errorMessage: "Timeout",
          durationMs: 5000,
        }),
      ],
    })

    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("Analyze competitors")).toBeInTheDocument()
    })

    // Switch to deliveries tab
    fireEvent.click(screen.getByRole("tab", { name: /Deliveries/i }))

    expect(screen.getByText("WebChatChannel")).toBeInTheDocument()
    expect(screen.getByText("TelegramChannel")).toBeInTheDocument()
    expect(screen.getByText("delivered")).toBeInTheDocument()
    expect(screen.getByText("failed")).toBeInTheDocument()
    expect(screen.getByText("Timeout")).toBeInTheDocument()
  })

  it("renders data-role attribute on dialog content", async () => {
    setupMocks()
    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("Analyze competitors")).toBeInTheDocument()
    })

    expect(
      document.querySelector('[data-role="task-detail-dialog"]')
    ).toBeInTheDocument()
  })

  it("does not fetch data when closed", () => {
    setupMocks()
    renderDialog("task-1", false)

    expect(mockGetTask).not.toHaveBeenCalled()
    expect(mockGetTaskAudit).not.toHaveBeenCalled()
  })

  it("does not fetch data when taskId is null", () => {
    setupMocks()
    renderDialog(null, true)

    expect(mockGetTask).not.toHaveBeenCalled()
  })

  it("formats duration correctly", async () => {
    setupMocks({
      auditLogs: [
        makeAuditLog({ id: 1, eventType: "step1", durationMs: 500 }),
        makeAuditLog({ id: 2, eventType: "step2", durationMs: 65000 }),
      ],
    })

    renderDialog()

    await waitFor(() => {
      expect(screen.getByText("500ms")).toBeInTheDocument()
    })
    expect(screen.getByText("1m 5s")).toBeInTheDocument()
  })
})

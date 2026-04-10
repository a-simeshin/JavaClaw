import { apiFetch, apiJson } from "@/api/http"

export type TaskStatus =
  | "todo"
  | "in_progress"
  | "completed"
  | "failed"
  | "awaiting_human_input"
  | "cancelled"

export type NotifyPolicy =
  | "silent"
  | "done_only"
  | "on_error"
  | "on_success"
  | "state_changes"

export type TaskRuntime = "inline" | "async" | "cron"

export interface TaskDto {
  id: string
  name: string
  description: string | null
  status: TaskStatus | null
  feedback: string | null
  conversationId: string | null
  parentTaskId: string | null
  notifyPolicy: NotifyPolicy | null
  runtimeType: TaskRuntime | null
  timeoutSeconds: number | null
  carryOverContext: boolean | null
  userId: string | null
  createdAt: string | null
  updatedAt: string | null
  failedAt: string | null
  cancelledAt: string | null
}

export function listTasks(params?: {
  userId?: string
  status?: TaskStatus
}): Promise<TaskDto[]> {
  const query = new URLSearchParams()
  if (params?.userId) query.set("userId", params.userId)
  if (params?.status) query.set("status", params.status)
  const qs = query.toString()
  return apiJson<TaskDto[]>(`/api/tasks${qs ? `?${qs}` : ""}`, {
    method: "GET",
  })
}

export function getTask(id: string): Promise<TaskDto> {
  return apiJson<TaskDto>(`/api/tasks/${encodeURIComponent(id)}`, {
    method: "GET",
  })
}

export function getChildTasks(parentTaskId: string): Promise<TaskDto[]> {
  return apiJson<TaskDto[]>(
    `/api/tasks/${encodeURIComponent(parentTaskId)}/children`,
    { method: "GET" },
  )
}

export async function cancelTask(id: string): Promise<void> {
  const response = await apiFetch(
    `/api/tasks/${encodeURIComponent(id)}/cancel`,
    { method: "POST" },
  )
  if (!response.ok) {
    throw new Error(`Failed to cancel task: ${response.status}`)
  }
}

export async function deleteTask(id: string): Promise<void> {
  return apiJson<void>(`/api/tasks/${encodeURIComponent(id)}`, {
    method: "DELETE",
  })
}

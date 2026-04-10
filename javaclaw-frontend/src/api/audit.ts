import { apiJson } from "@/api/http"

export interface TaskAuditLogDto {
  id: number
  taskId: string
  executionId: string | null
  eventType: string
  createdAt: string
  systemPrompt: string | null
  userPrompt: string | null
  toolName: string | null
  toolArgs: string | null
  toolResult: string | null
  toolDurationMs: number | null
  llmRequest: string | null
  llmResponse: string | null
  tokenUsage: string | null
  errorMessage: string | null
  errorTrace: string | null
  durationMs: number | null
  metadata: string | null
}

export interface TaskExecutionDto {
  id: string
  taskId: string
  executionNumber: number
  status: string
  systemPrompt: string | null
  userPrompt: string | null
  llmResponse: string | null
  errorMessage: string | null
  errorTrace: string | null
  startedAt: string
  completedAt: string | null
  durationMs: number | null
}

export interface DeliveryAuditLogDto {
  id: number
  taskId: string | null
  conversationId: string
  channelName: string
  message: string
  status: string
  attempts: number
  errorMessage: string | null
  durationMs: number | null
  createdAt: string
}

export interface ChatAuditLogDto {
  id: number
  conversationId: string
  method: string
  userContent: string | null
  response: string | null
  error: string | null
  durationMs: number | null
  userId: string | null
  toolCallsDetail: string | null
  tokenUsage: string | null
  createdAt: string
}

export function getTaskAudit(taskId: string): Promise<TaskAuditLogDto[]> {
  return apiJson<TaskAuditLogDto[]>(
    `/api/tasks/${encodeURIComponent(taskId)}/audit`,
    { method: "GET" },
  )
}

export function getTaskExecutions(
  taskId: string,
): Promise<TaskExecutionDto[]> {
  return apiJson<TaskExecutionDto[]>(
    `/api/tasks/${encodeURIComponent(taskId)}/executions`,
    { method: "GET" },
  )
}

export function getDeliveryAudit(
  taskId: string,
): Promise<DeliveryAuditLogDto[]> {
  return apiJson<DeliveryAuditLogDto[]>(
    `/api/tasks/${encodeURIComponent(taskId)}/deliveries`,
    { method: "GET" },
  )
}

export function getChatAudit(
  conversationId: string,
  limit = 20,
): Promise<ChatAuditLogDto[]> {
  return apiJson<ChatAuditLogDto[]>(
    `/api/audit/chat?conversationId=${encodeURIComponent(conversationId)}&limit=${limit}`,
    { method: "GET" },
  )
}

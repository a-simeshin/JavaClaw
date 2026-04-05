import { apiJson } from "@/api/http"

export interface ConversationDto {
  id: string
  title: string
  createdAt: string
  updatedAt: string
  messageCount?: number
}

export interface MessageDto {
  id: string
  role: "user" | "assistant" | "system" | "tool"
  content: string
  createdAt: string
  parts?: unknown[]
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  total: number
}

export function listConversations(
  page = 0,
  size = 20,
): Promise<Page<ConversationDto>> {
  return apiJson<Page<ConversationDto>>(
    `/api/conversations?page=${page}&size=${size}`,
    { method: "GET" },
  )
}

export function listConversationMessages(
  conversationId: string,
  page = 0,
  size = 200,
): Promise<Page<MessageDto>> {
  return apiJson<Page<MessageDto>>(
    `/api/conversations/${encodeURIComponent(conversationId)}/messages?page=${page}&size=${size}`,
    { method: "GET" },
  )
}

export function createConversation(): Promise<{ id: string }> {
  return apiJson<{ id: string }>("/api/conversations", {
    method: "POST",
    body: {},
  })
}

export function deleteConversation(id: string): Promise<void> {
  return apiJson<void>(`/api/conversations/${encodeURIComponent(id)}`, {
    method: "DELETE",
  })
}

import { apiJson } from "@/api/http"

export type ApprovalStatus = "pending" | "approved" | "denied" | "timeout"

export interface ApprovalRequestDto {
  id: string
  taskId: string
  conversationId: string
  question: string
  response: string | null
  status: ApprovalStatus
  timeoutAt: string
  createdAt: string
  respondedAt: string | null
}

export function getPendingApprovals(
  conversationId: string,
): Promise<ApprovalRequestDto[]> {
  return apiJson<ApprovalRequestDto[]>(
    `/api/chat/approval/pending?conversationId=${encodeURIComponent(conversationId)}`,
    { method: "GET" },
  )
}

export function respondToApproval(
  approvalId: string,
  response: string,
): Promise<void> {
  return apiJson<void>(
    `/api/chat/approval/${encodeURIComponent(approvalId)}/respond`,
    {
      method: "POST",
      body: { response },
    },
  )
}

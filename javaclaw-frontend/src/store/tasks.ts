import { atom } from "jotai"
import type { ApprovalRequestDto } from "@/api/approvals"

export const PENDING_APPROVALS_INITIAL: ApprovalRequestDto[] = []
export const ACTIVE_TASK_COUNT_INITIAL = 0
export const UNREAD_NOTIFICATIONS_INITIAL: Record<string, number> = {}

export const pendingApprovalsAtom = atom<ApprovalRequestDto[]>(PENDING_APPROVALS_INITIAL)
export const activeTaskCountAtom = atom<number>(ACTIVE_TASK_COUNT_INITIAL)
export const unreadNotificationsAtom = atom<Record<string, number>>(UNREAD_NOTIFICATIONS_INITIAL)

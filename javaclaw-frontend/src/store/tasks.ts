import { atom } from "jotai"
import type { ApprovalRequestDto } from "@/api/approvals"

export const pendingApprovalsAtom = atom<ApprovalRequestDto[]>([])
export const activeTaskCountAtom = atom<number>(0)
export const unreadNotificationsAtom = atom<Record<string, number>>({})

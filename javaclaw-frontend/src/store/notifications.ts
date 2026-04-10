import { atom } from "jotai"

export interface TaskNotification {
  id: string
  taskId: string
  taskName: string
  status: string
  message: string
  conversationId: string
  timestamp: string
}

export const notificationQueueAtom = atom<TaskNotification[]>([])

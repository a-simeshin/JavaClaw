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

export const NOTIFICATION_QUEUE_INITIAL: TaskNotification[] = []
export const notificationQueueAtom = atom<TaskNotification[]>(NOTIFICATION_QUEUE_INITIAL)

import { useQueryClient } from "@tanstack/react-query"
import { useSetAtom } from "jotai"
import { useCallback } from "react"

import {
  activeConversationIdAtom,
  ACTIVE_CONVERSATION_INITIAL,
  isStreamingAtom,
  IS_STREAMING_INITIAL,
} from "@/store/chat"
import {
  conversationSearchAtom,
  CONVERSATION_SEARCH_INITIAL,
} from "@/store/conversations"
import {
  notificationQueueAtom,
  NOTIFICATION_QUEUE_INITIAL,
} from "@/store/notifications"
import {
  pendingApprovalsAtom,
  PENDING_APPROVALS_INITIAL,
  activeTaskCountAtom,
  ACTIVE_TASK_COUNT_INITIAL,
  unreadNotificationsAtom,
  UNREAD_NOTIFICATIONS_INITIAL,
} from "@/store/tasks"

/** All per-user atoms with their initial values. Iterated by exhaustiveness tests. */
export const CLIENT_RESET_ATOMS = [
  [activeConversationIdAtom, ACTIVE_CONVERSATION_INITIAL],
  [isStreamingAtom, IS_STREAMING_INITIAL],
  [conversationSearchAtom, CONVERSATION_SEARCH_INITIAL],
  [notificationQueueAtom, NOTIFICATION_QUEUE_INITIAL],
  [pendingApprovalsAtom, PENDING_APPROVALS_INITIAL],
  [activeTaskCountAtom, ACTIVE_TASK_COUNT_INITIAL],
  [unreadNotificationsAtom, UNREAD_NOTIFICATIONS_INITIAL],
] as const

/**
 * Returns a stable callback that wipes all per-user client state.
 * Call it at every session boundary (logout, login, user-mismatch on restore).
 *
 * Uses queryClient.clear() — hard wipe, no stale data flash for new user.
 * The 401 auto-redirect path (api/http.ts) does a full page reload and is
 * intentionally NOT covered here — browser wipe handles that path.
 */
export function useResetClientState(): () => void {
  const setActiveConversationId = useSetAtom(activeConversationIdAtom)
  const setIsStreaming = useSetAtom(isStreamingAtom)
  const setConversationSearch = useSetAtom(conversationSearchAtom)
  const setNotificationQueue = useSetAtom(notificationQueueAtom)
  const setPendingApprovals = useSetAtom(pendingApprovalsAtom)
  const setActiveTaskCount = useSetAtom(activeTaskCountAtom)
  const setUnreadNotifications = useSetAtom(unreadNotificationsAtom)
  const queryClient = useQueryClient()

  return useCallback(() => {
    setActiveConversationId(ACTIVE_CONVERSATION_INITIAL)
    setIsStreaming(IS_STREAMING_INITIAL)
    setConversationSearch(CONVERSATION_SEARCH_INITIAL)
    setNotificationQueue(NOTIFICATION_QUEUE_INITIAL)
    setPendingApprovals(PENDING_APPROVALS_INITIAL)
    setActiveTaskCount(ACTIVE_TASK_COUNT_INITIAL)
    setUnreadNotifications(UNREAD_NOTIFICATIONS_INITIAL)
    queryClient.clear()
  }, [
    setActiveConversationId,
    setIsStreaming,
    setConversationSearch,
    setNotificationQueue,
    setPendingApprovals,
    setActiveTaskCount,
    setUnreadNotifications,
    queryClient,
  ])
}

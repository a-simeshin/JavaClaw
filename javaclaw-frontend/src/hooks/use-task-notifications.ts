import { useEffect, useRef } from "react"
import { useSetAtom } from "jotai"
import { useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"

import { unreadNotificationsAtom } from "@/store/tasks"

/**
 * Subscribes to SSE task notifications for a given conversation.
 * Cookies are included automatically (withCredentials is not supported on
 * EventSource, but the session cookie is sent by default for same-origin
 * requests).  No ?auth= parameter needed.
 * On notification: increments unread count, shows toast, invalidates queries.
 */
export function useTaskNotifications(conversationId: string | null) {
  const setUnread = useSetAtom(unreadNotificationsAtom)
  const queryClient = useQueryClient()
  const eventSourceRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!conversationId) return

    const url = `/api/chat/notifications/${encodeURIComponent(conversationId)}`

    const es = new EventSource(url)
    eventSourceRef.current = es

    es.onmessage = (event) => {
      try {
        const data = JSON.parse(event.data)

        setUnread((prev) => ({
          ...prev,
          [conversationId]: (prev[conversationId] ?? 0) + 1,
        }))

        queryClient.invalidateQueries({
          queryKey: ["conversations", conversationId, "messages"],
        })

        if (data.status === "completed") {
          toast.success(`Task "${data.taskName ?? "Task"}" completed`)
        } else if (data.status === "failed") {
          toast.error(`Task "${data.taskName ?? "Task"}" failed`)
        } else if (data.status === "awaiting_human_input") {
          toast("Approval requested", {
            description: data.question ?? "A task needs your input",
            duration: Infinity,
          })
        }
      } catch {
        // non-JSON heartbeat or malformed event — ignore
      }
    }

    es.onerror = () => {
      if (es.readyState === EventSource.CLOSED) {
        // EventSource closed permanently (not a temporary error) — don't reconnect
        es.close()
        eventSourceRef.current = null
      }
    }

    return () => {
      es.close()
      eventSourceRef.current = null
    }
  }, [conversationId, setUnread, queryClient])
}

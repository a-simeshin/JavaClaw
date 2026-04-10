import { useState, useCallback } from "react"
import { useQueryClient } from "@tanstack/react-query"
import { toast } from "sonner"

import {
  respondToApproval,
  type ApprovalRequestDto,
} from "@/api/approvals"

/**
 * Hook for handling approval request interactions (approve/deny/timeout).
 * Returns the current state and action handlers for an approval request card.
 */
export function useApproval(approval: ApprovalRequestDto) {
  const [isSubmitting, setIsSubmitting] = useState(false)
  const [resolved, setResolved] = useState<"approved" | "denied" | "timeout" | null>(
    approval.status !== "pending" ? approval.status as "approved" | "denied" | "timeout" : null,
  )
  const queryClient = useQueryClient()

  const respond = useCallback(
    async (response: string) => {
      if (resolved || isSubmitting) return
      setIsSubmitting(true)
      try {
        await respondToApproval(approval.id, response)
        const isApproved = /^(да|yes|ок|ok|одобряю|approve)/i.test(response.trim())
        setResolved(isApproved ? "approved" : "denied")
        queryClient.invalidateQueries({
          queryKey: ["approvals", "pending"],
        })
        toast.success(isApproved ? "Approved" : "Denied")
      } catch {
        toast.error("Failed to submit response")
      } finally {
        setIsSubmitting(false)
      }
    },
    [approval.id, resolved, isSubmitting, queryClient],
  )

  const approve = useCallback(() => respond("да"), [respond])
  const deny = useCallback(() => respond("нет"), [respond])

  const isExpired =
    !resolved && new Date(approval.timeoutAt).getTime() < Date.now()

  if (isExpired && !resolved) {
    // Mark as timed out locally without API call
    if (resolved !== "timeout") {
      setResolved("timeout")
    }
  }

  return {
    resolved,
    isSubmitting,
    isExpired,
    approve,
    deny,
    respond,
  }
}

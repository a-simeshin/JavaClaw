import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { apiJson } from "@/api/http"

export interface CronJobDto {
  id: string
  name: string
  expression: string
  enabled: boolean
  lastRun?: string
  nextRun?: string
  status?: string
}

const KEY = ["cron-jobs"] as const

export function useCronJobs() {
  return useQuery({
    queryKey: KEY,
    queryFn: async () => {
      try {
        return await apiJson<CronJobDto[]>("/api/cron")
      } catch {
        return [] as CronJobDto[]
      }
    },
  })
}

export function useToggleCronJob() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, enabled }: { id: string; enabled: boolean }) =>
      apiJson<void>(`/api/cron/${id}`, { method: "PUT", body: { enabled } }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useTriggerCronJob() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiJson<void>(`/api/cron/${id}/trigger`, { method: "POST" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

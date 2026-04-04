import { useQuery } from "@tanstack/react-query"

import { apiFetch } from "@/api/http"

async function fetchLogfile(): Promise<string> {
  const res = await apiFetch("/actuator/logfile")
  if (!res.ok) {
    if (res.status === 404) return ""
    throw new Error(`HTTP ${res.status}`)
  }
  return res.text()
}

export function useLogfile(follow: boolean) {
  return useQuery({
    queryKey: ["logfile"],
    queryFn: fetchLogfile,
    refetchInterval: follow ? 2_000 : false,
    refetchOnWindowFocus: follow,
  })
}

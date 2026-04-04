import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { apiFetch, apiJson } from "@/api/http"

export interface SkillDto {
  id?: string
  name: string
  description: string
  enabled: boolean
  createdAt?: string
}

const KEY = ["skills"] as const

export function useSkills() {
  return useQuery({
    queryKey: KEY,
    queryFn: () => apiJson<SkillDto[]>("/api/skills"),
  })
}

export function useCreateSkill() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: SkillDto) =>
      apiJson<{ id: string }>("/api/skills", { method: "POST", body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUpdateSkill() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: SkillDto }) =>
      apiJson<void>(`/api/skills/${id}`, { method: "PUT", body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useDeleteSkill() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch(`/api/skills/${id}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

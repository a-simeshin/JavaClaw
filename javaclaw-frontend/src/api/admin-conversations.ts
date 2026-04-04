import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { apiFetch, apiJson } from "@/api/http"

export interface AdminConversationDto {
  id: string
  title: string
  owner: string
  messageCount: number
  createdAt: string
  updatedAt: string
}

const KEY = ["admin-conversations"] as const

export function useAdminConversations(page: number, size: number) {
  return useQuery({
    queryKey: [...KEY, page, size],
    queryFn: () =>
      apiJson<{
        items: AdminConversationDto[]
        total: number
      }>(`/api/admin/conversations?page=${page}&size=${size}`),
  })
}

export function useDeleteAdminConversation() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch(`/api/admin/conversations/${id}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { apiFetch, apiJson } from "@/api/http"

export type McpType = "stdio" | "http"

export interface McpServerDto {
  id?: string
  name: string
  type: McpType
  config: string
  enabled: boolean
  status?: string
}

export interface McpStatusDto {
  id: string
  status: string
  detail?: string
  checkedAt?: string
}

export interface ToolCacheInfoDto {
  toolNames: string[]
  count: number
}

const KEY = ["mcp-servers"] as const

export function useMcpServers() {
  return useQuery({
    queryKey: KEY,
    queryFn: () => apiJson<McpServerDto[]>("/api/mcp-servers"),
  })
}

export function useMcpStatus(id: string | undefined) {
  return useQuery({
    queryKey: ["mcp-servers", id, "status"],
    queryFn: () => apiJson<McpStatusDto>(`/api/mcp-servers/${id}/status`),
    enabled: Boolean(id),
    refetchInterval: 10_000,
  })
}

export function useMcpTools() {
  return useQuery({
    queryKey: ["mcp-servers", "tools"],
    queryFn: () => apiJson<ToolCacheInfoDto>("/api/mcp-servers/tools"),
    refetchInterval: 30_000,
  })
}

export function useCreateMcpServer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (body: McpServerDto) =>
      apiJson<{ id: string }>("/api/mcp-servers", { method: "POST", body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useUpdateMcpServer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ id, body }: { id: string; body: McpServerDto }) =>
      apiJson<void>(`/api/mcp-servers/${id}`, { method: "PUT", body }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

export function useDeleteMcpServer() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (id: string) =>
      apiFetch(`/api/mcp-servers/${id}`, { method: "DELETE" }),
    onSuccess: () => qc.invalidateQueries({ queryKey: KEY }),
  })
}

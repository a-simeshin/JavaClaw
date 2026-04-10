import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { apiJson } from "@/api/http"

export interface FileContent {
  path: string
  content: string
}

export interface FileNode {
  path: string
  name: string
  type: "file" | "dir"
  size: number
  children: FileNode[] | null
}

export function useFileTree() {
  return useQuery({
    queryKey: ["files", "__tree__"],
    queryFn: () => apiJson<FileNode>("/api/files"),
  })
}

export function useFileContent(path: string | null) {
  return useQuery({
    queryKey: ["files", path],
    queryFn: () =>
      apiJson<FileContent>(`/api/files/${encodeURIComponent(path!)}`),
    enabled: Boolean(path),
  })
}

export function useSaveFile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ path, content }: { path: string; content: string }) =>
      apiJson<void>(`/api/files/${encodeURIComponent(path)}`, {
        method: "PUT",
        body: { content },
      }),
    onSuccess: (_, vars) => {
      qc.invalidateQueries({ queryKey: ["files", vars.path] })
      qc.invalidateQueries({ queryKey: ["files", "__tree__"] })
    },
  })
}

export function useCreateFile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ path, content }: { path: string; content?: string }) =>
      apiJson<FileContent>("/api/files", {
        method: "POST",
        body: { path, content: content ?? "" },
      }),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["files", "__tree__"] }),
  })
}

export function useDeleteFile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: (path: string) =>
      apiJson<void>(`/api/files/${encodeURIComponent(path)}`, {
        method: "DELETE",
      }),
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["files", "__tree__"] }),
  })
}

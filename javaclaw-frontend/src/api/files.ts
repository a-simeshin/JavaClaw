import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { apiFetch, apiJson } from "@/api/http"

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

export function useUploadFile() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: async ({
      file,
      path,
    }: {
      file: File
      path?: string
    }): Promise<FileContent> => {
      const form = new FormData()
      form.append("file", file)
      if (path) form.append("path", path)
      const res = await apiFetch("/api/files/upload", {
        method: "POST",
        body: form,
      })
      if (!res.ok) throw new Error(`Upload failed: ${res.status}`)
      return res.json()
    },
    onSuccess: () =>
      qc.invalidateQueries({ queryKey: ["files", "__tree__"] }),
  })
}

export async function downloadFile(path: string): Promise<void> {
  const res = await apiFetch(
    `/api/files/download/${encodeURIComponent(path)}`,
  )
  if (!res.ok) throw new Error(`Download failed: ${res.status}`)
  const blob = await res.blob()
  const url = URL.createObjectURL(blob)
  const a = document.createElement("a")
  a.href = url
  a.download = path.includes("/") ? path.split("/").pop()! : path
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}

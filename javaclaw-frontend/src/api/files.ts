import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query"

import { apiJson } from "@/api/http"

export interface FileContent {
  path: string
  content: string
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
    onSuccess: (_, vars) =>
      qc.invalidateQueries({ queryKey: ["files", vars.path] }),
  })
}

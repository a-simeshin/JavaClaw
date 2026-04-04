import { apiJson } from "@/api/http"

export interface MeDto {
  username: string
  role: string
}

export function getMe(authHeader?: string): Promise<MeDto> {
  const headers: HeadersInit = {}
  if (authHeader) {
    headers["Authorization"] = authHeader
  }
  return apiJson<MeDto>("/api/me", {
    method: "GET",
    headers,
    skipAuthRedirect: true,
  })
}

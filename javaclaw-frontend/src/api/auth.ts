import { apiJson, apiFetch } from "./http"

export interface UserInfo {
  id: string
  username: string
  email: string | null
  roles: string[]
  authorities: string[]
}

export interface LoginResponse {
  user: UserInfo
  sessionExpiresAt: string
}

export interface SessionInfo {
  id: string
  createdAt: string
  lastUsedAt: string
  expiresAt: string
  remoteAddr: string | null
  userAgent: string | null
}

export async function login(username: string, password: string): Promise<LoginResponse> {
  return apiJson<LoginResponse>("/api/auth/login", {
    method: "POST",
    body: { username, password },
  })
}

export async function logout(): Promise<void> {
  await apiFetch("/api/auth/logout", { method: "POST", skipAuthRedirect: true })
}

export async function logoutAll(): Promise<void> {
  await apiFetch("/api/auth/logout-all", { method: "POST", skipAuthRedirect: true })
}

export async function getMe(): Promise<UserInfo> {
  return apiJson<UserInfo>("/api/auth/me")
}

export async function listSessions(): Promise<SessionInfo[]> {
  return apiJson<SessionInfo[]>("/api/auth/sessions")
}

export async function revokeSession(id: string): Promise<void> {
  await apiFetch(`/api/auth/sessions/${id}`, { method: "DELETE" })
}

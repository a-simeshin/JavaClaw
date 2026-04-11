import { atom } from "jotai"
import type { UserInfo } from "@/api/auth"

export type { UserInfo }

export interface AuthState {
  user: UserInfo | null
  sessionExpiresAt: string | null
  isLoading: boolean
}

export const authAtom = atom<AuthState>({
  user: null,
  sessionExpiresAt: null,
  isLoading: true,
})

export const isAuthenticatedAtom = atom(
  (get) => get(authAtom).user !== null,
)

export const authUserAtom = atom((get) => {
  const state = get(authAtom)
  return state.user
})

// ---------------------------------------------------------------------------
// Legacy no-op stubs — kept for backward compatibility with __root.tsx and
// any other callers that have not been migrated yet.
// ---------------------------------------------------------------------------

/** @deprecated Credentials are now stored in a session cookie, not localStorage. */
export function getStoredCredentials(): string | null {
  return null
}

/** @deprecated Use authAtom directly. */
export function persistAuth(_state: unknown): void {
  // no-op
}

/** @deprecated Credentials are now managed server-side. */
export function encodeBasicCredentials(
  _username: string,
  _password: string,
): string {
  return ""
}

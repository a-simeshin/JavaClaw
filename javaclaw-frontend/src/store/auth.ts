import { atom } from "jotai"

const STORAGE_CREDENTIALS = "javaclaw.auth.credentials"
const STORAGE_USERNAME = "javaclaw.auth.username"
const STORAGE_ROLE = "javaclaw.auth.role"

function readStoredCredentials(): string | null {
  if (typeof window === "undefined") return null
  return window.localStorage.getItem(STORAGE_CREDENTIALS)
}

function readStoredUsername(): string | null {
  if (typeof window === "undefined") return null
  return window.localStorage.getItem(STORAGE_USERNAME)
}

function readStoredRole(): string | null {
  if (typeof window === "undefined") return null
  return window.localStorage.getItem(STORAGE_ROLE)
}

export interface AuthState {
  credentials: string | null
  username: string | null
  role: string | null
}

export const authAtom = atom<AuthState>({
  credentials: readStoredCredentials(),
  username: readStoredUsername(),
  role: readStoredRole(),
})

export const isAuthenticatedAtom = atom(
  (get) => get(authAtom).credentials !== null,
)

export const authUserAtom = atom((get) => {
  const state = get(authAtom)
  return { username: state.username, role: state.role }
})

export function encodeBasicCredentials(
  username: string,
  password: string,
): string {
  if (typeof window === "undefined") {
    return Buffer.from(`${username}:${password}`, "utf-8").toString("base64")
  }
  return window.btoa(`${username}:${password}`)
}

export function persistAuth(state: AuthState) {
  if (typeof window === "undefined") return
  const { credentials, username, role } = state
  if (credentials) {
    window.localStorage.setItem(STORAGE_CREDENTIALS, credentials)
  } else {
    window.localStorage.removeItem(STORAGE_CREDENTIALS)
  }
  if (username) {
    window.localStorage.setItem(STORAGE_USERNAME, username)
  } else {
    window.localStorage.removeItem(STORAGE_USERNAME)
  }
  if (role) {
    window.localStorage.setItem(STORAGE_ROLE, role)
  } else {
    window.localStorage.removeItem(STORAGE_ROLE)
  }
}

export function getStoredCredentials(): string | null {
  return readStoredCredentials()
}

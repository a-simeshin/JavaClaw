import { useAtom, useAtomValue } from "jotai"
import { useCallback } from "react"

import { getMe } from "@/api/system"
import {
  authAtom,
  authUserAtom,
  encodeBasicCredentials,
  isAuthenticatedAtom,
  persistAuth,
} from "@/store/auth"

export function useAuth() {
  const [auth, setAuth] = useAtom(authAtom)
  const isAuthenticated = useAtomValue(isAuthenticatedAtom)
  const user = useAtomValue(authUserAtom)

  const login = useCallback(
    async (username: string, password: string) => {
      const credentials = encodeBasicCredentials(username, password)
      // Verify credentials against /api/me before persisting.
      const me = await getMe(`Basic ${credentials}`)
      const next = {
        credentials,
        username: me.username,
        role: me.role,
      }
      persistAuth(next)
      setAuth(next)
      return me
    },
    [setAuth],
  )

  const logout = useCallback(() => {
    const next = { credentials: null, username: null, role: null }
    persistAuth(next)
    setAuth(next)
  }, [setAuth])

  return {
    auth,
    user,
    isAuthenticated,
    login,
    logout,
  }
}

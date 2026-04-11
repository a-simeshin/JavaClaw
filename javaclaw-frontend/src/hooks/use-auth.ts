import { useAtom, useAtomValue } from "jotai"
import { useCallback, useEffect, useRef } from "react"

import { authAtom, authUserAtom, isAuthenticatedAtom } from "@/store/auth"
import * as authApi from "@/api/auth"
import { ensureCsrfCookie } from "@/lib/csrf"
import { useResetClientState } from "@/lib/client-reset"

export function useAuth() {
  const [auth, setAuth] = useAtom(authAtom)
  const isAuthenticated = useAtomValue(isAuthenticatedAtom)
  const user = useAtomValue(authUserAtom)
  const resetClientState = useResetClientState()
  const prevUserIdRef = useRef<string | null>(null)

  // On mount: restore session from cookie via /api/auth/me
  useEffect(() => {
    ensureCsrfCookie()
    authApi
      .getMe()
      .then((u) => {
        if (prevUserIdRef.current !== null && prevUserIdRef.current !== u.id) {
          resetClientState()
        }
        prevUserIdRef.current = u.id ?? null
        setAuth({ user: u, sessionExpiresAt: null, isLoading: false })
      })
      .catch(() =>
        setAuth({ user: null, sessionExpiresAt: null, isLoading: false }),
      )
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = useCallback(
    async (username: string, password: string) => {
      const response = await authApi.login(username, password)
      resetClientState()
      setAuth({
        user: response.user,
        sessionExpiresAt: response.sessionExpiresAt,
        isLoading: false,
      })
      return response
    },
    [setAuth, resetClientState],
  )

  const logout = useCallback(async () => {
    await authApi.logout()
    setAuth({ user: null, sessionExpiresAt: null, isLoading: false })
  }, [setAuth])

  return {
    auth,
    user,
    isAuthenticated,
    login,
    logout,
  }
}

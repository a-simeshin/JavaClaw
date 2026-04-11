import { useAtom, useAtomValue } from "jotai"
import { useCallback, useEffect } from "react"

import { authAtom, authUserAtom, isAuthenticatedAtom } from "@/store/auth"
import * as authApi from "@/api/auth"
import { ensureCsrfCookie } from "@/lib/csrf"

export function useAuth() {
  const [auth, setAuth] = useAtom(authAtom)
  const isAuthenticated = useAtomValue(isAuthenticatedAtom)
  const user = useAtomValue(authUserAtom)

  // On mount: restore session from cookie via /api/auth/me
  useEffect(() => {
    ensureCsrfCookie()
    authApi
      .getMe()
      .then((u) =>
        setAuth({ user: u, sessionExpiresAt: null, isLoading: false }),
      )
      .catch(() =>
        setAuth({ user: null, sessionExpiresAt: null, isLoading: false }),
      )
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const login = useCallback(
    async (username: string, password: string) => {
      const response = await authApi.login(username, password)
      setAuth({
        user: response.user,
        sessionExpiresAt: response.sessionExpiresAt,
        isLoading: false,
      })
      return response
    },
    [setAuth],
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

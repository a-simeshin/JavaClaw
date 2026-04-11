import {
  createRootRoute,
  Outlet,
  redirect,
  useLocation,
} from "@tanstack/react-router"

import { AppLayout } from "@/components/app-layout"
import { getMe } from "@/api/auth"

export const Route = createRootRoute({
  beforeLoad: async ({ location }) => {
    const isLogin = location.pathname === "/login"
    if (isLogin) return
    try {
      await getMe()
    } catch {
      throw redirect({ to: "/login" })
    }
  },
  component: RootComponent,
})

function RootComponent() {
  const location = useLocation()
  const isLogin = location.pathname === "/login"

  if (isLogin) {
    return <Outlet />
  }

  return (
    <AppLayout>
      <Outlet />
    </AppLayout>
  )
}

import {
  createRootRoute,
  Outlet,
  redirect,
  useLocation,
} from "@tanstack/react-router"

import { AppLayout } from "@/components/app-layout"
import { getStoredCredentials } from "@/store/auth"

export const Route = createRootRoute({
  beforeLoad: ({ location }) => {
    const isLogin = location.pathname === "/login"
    const hasCredentials = getStoredCredentials() !== null
    if (!hasCredentials && !isLogin) {
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

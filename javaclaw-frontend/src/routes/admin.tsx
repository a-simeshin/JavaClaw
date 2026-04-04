import { createFileRoute, Link, Outlet, redirect } from "@tanstack/react-router"

import { cn } from "@/lib/utils"

const ADMIN_TABS: { to: string; label: string }[] = [
  { to: "/admin/skills", label: "Skills" },
  { to: "/admin/mcp", label: "MCP" },
  { to: "/admin/prompts", label: "Prompts" },
]

function getRole(): string | null {
  if (typeof window === "undefined") return null
  return window.localStorage.getItem("javaclaw.auth.role")
}

export const Route = createFileRoute("/admin")({
  beforeLoad: () => {
    const role = getRole()
    // Allow ADMIN role. If unauthenticated, let chat route handle login.
    if (role && role !== "ADMIN") {
      throw redirect({ to: "/chat" })
    }
  },
  component: AdminLayout,
})

function AdminLayout() {
  return (
    <div className="flex h-full flex-col">
      <div className="border-b border-border bg-background">
        <div className="flex items-end gap-1 px-6 pt-4">
          <h1 className="mr-6 font-mono text-[16px] font-bold leading-[1.2] tracking-[-0.02em] text-foreground">
            Admin
          </h1>
          <nav
            aria-label="Admin sections"
            className="flex items-center gap-0.5"
          >
            {ADMIN_TABS.map((tab) => (
              <Link
                key={tab.to}
                to={tab.to}
                className={cn(
                  "relative px-3 py-2 text-[13px] text-muted-foreground transition-colors",
                  "hover:text-foreground",
                )}
                activeProps={{
                  className:
                    "text-foreground after:absolute after:inset-x-3 after:-bottom-px after:h-px after:bg-foreground",
                }}
              >
                {tab.label}
              </Link>
            ))}
          </nav>
        </div>
      </div>
      <div className="min-h-0 flex-1 overflow-auto">
        <Outlet />
      </div>
    </div>
  )
}

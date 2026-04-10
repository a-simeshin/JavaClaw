import {
  IconBolt,
  IconChecklist,
  IconClock,
  IconDashboard,
  IconFiles,
  IconFileText,
  IconMessage,
  IconMessages,
  IconPlug,
  IconSettings,
  IconAdjustments,
  IconTerminal2,
} from "@tabler/icons-react"
import { Link, useLocation } from "@tanstack/react-router"
import type { ComponentType, ReactNode, SVGProps } from "react"
import { useTranslation } from "react-i18next"

import { useHealth } from "@/api/config"
import { ConversationHistoryMenu } from "@/components/chat/conversation-history-menu"
import { Separator } from "@/components/ui/separator"
import { cn } from "@/lib/utils"

interface NavItem {
  to: string
  labelKey: string
  fallback: string
  icon: ComponentType<SVGProps<SVGSVGElement>>
}

const NAV_ITEMS: NavItem[] = [
  { to: "/chat", labelKey: "nav.chat", fallback: "Chat", icon: IconMessage },
  { to: "/files", labelKey: "nav.files", fallback: "Files", icon: IconFiles },
  { to: "/tasks", labelKey: "nav.tasks", fallback: "Tasks", icon: IconChecklist },
  { to: "/overview", labelKey: "nav.overview", fallback: "Overview", icon: IconDashboard },
]

const ADMIN_ITEMS: NavItem[] = [
  { to: "/admin/skills", labelKey: "nav.skills", fallback: "Skills", icon: IconBolt },
  { to: "/admin/mcp", labelKey: "nav.mcp", fallback: "MCP", icon: IconPlug },
  { to: "/admin/prompts", labelKey: "nav.prompts", fallback: "Prompts", icon: IconFileText },
]

const OPS_ITEMS: NavItem[] = [
  { to: "/logs", labelKey: "nav.logs", fallback: "Logs", icon: IconTerminal2 },
  { to: "/cron", labelKey: "nav.cron", fallback: "Cron", icon: IconClock },
  { to: "/config", labelKey: "nav.config", fallback: "Config", icon: IconAdjustments },
  { to: "/conversations", labelKey: "nav.conversations", fallback: "Conversations", icon: IconMessages },
]

const FOOTER_ITEMS: NavItem[] = [
  { to: "/settings", labelKey: "nav.settings", fallback: "Settings", icon: IconSettings },
]

/**
 * Primary navigation sidebar. Per design ctx v3 §5:
 *  - Logo: JetBrains Mono 20px/700, accent word highlighted.
 *  - Section labels: JetBrains Mono 9px/600, 0.12em tracking, uppercase.
 *  - Nav items: Outfit 14px, gap-12px, rounded-sm, active = accent bg + accent dot.
 */
export function AppSidebar() {
  const { t } = useTranslation()
  const location = useLocation()
  const isChatRoute = location.pathname.startsWith("/chat")

  return (
    <nav
      aria-label="Primary"
      className="flex h-full min-h-0 flex-col"
    >
      <div className="border-b border-border px-6 pt-7 pb-5">
        <span className="font-mono text-[20px] font-bold leading-none tracking-[-0.03em] text-foreground">
          java<span className="text-accent-strong">Claw</span>
        </span>
      </div>
      <SidebarSectionLabel>
        {t("nav.section.navigation", { defaultValue: "Navigation" })}
      </SidebarSectionLabel>
      <ul className="flex flex-col gap-px px-3">
        {NAV_ITEMS.map((item) => (
          <SidebarNavLink key={item.to} item={item} t={t} />
        ))}
      </ul>
      {isChatRoute && (
        <>
          <Separator className="mx-3 my-3 w-auto bg-separator" />
          <div className="min-h-0 flex-1 overflow-y-auto">
            <ConversationHistoryMenu />
          </div>
        </>
      )}
      {!isChatRoute && (
        <>
          <SidebarSectionLabel>
            {t("nav.section.admin", { defaultValue: "Admin" })}
          </SidebarSectionLabel>
          <ul className="flex flex-col gap-px px-3">
            {ADMIN_ITEMS.map((item) => (
              <SidebarNavLink key={item.to} item={item} t={t} />
            ))}
          </ul>
          <SidebarSectionLabel>
            {t("nav.section.ops", { defaultValue: "Operations" })}
          </SidebarSectionLabel>
          <ul className="flex flex-col gap-px px-3">
            {OPS_ITEMS.map((item) => (
              <SidebarNavLink key={item.to} item={item} t={t} />
            ))}
          </ul>
          <div className="flex-1" />
          <ul className="flex flex-col gap-px border-t border-border px-3 pt-3">
            {FOOTER_ITEMS.map((item) => (
              <SidebarNavLink key={item.to} item={item} t={t} />
            ))}
          </ul>
        </>
      )}
      <SidebarStatusFooter />
    </nav>
  )
}

/**
 * Sidebar footer — service identity + live backend health.
 * Per design ctx v3 §10.4 "Status is not optional" AND §12 "every entity has
 * a state, always visible". Status is derived from /actuator/health so it
 * never lies about backend state.
 */
function SidebarStatusFooter() {
  const { t } = useTranslation()
  const health = useHealth()
  const { tone, labelKey, fallback } = resolveHealth(health)

  return (
    <div className="mt-auto flex items-center gap-3 border-t border-border px-6 py-[18px]">
      <div
        aria-hidden
        className="flex h-9 w-9 shrink-0 items-center justify-center rounded-md bg-primary font-mono text-[14px] font-bold leading-none text-primary-foreground"
      >
        J
      </div>
      <div className="min-w-0 flex-1">
        <div className="truncate font-mono text-[14px] font-semibold leading-tight text-foreground">
          {t("app.service.name", { defaultValue: "javaclaw" })}
        </div>
        <div className="mt-[3px] flex items-center gap-1.5 text-[12px] leading-tight text-muted-foreground">
          <span
            aria-hidden
            className={cn(
              "inline-block h-1.5 w-1.5 rounded-full shrink-0",
              tone === "ok" && "bg-success-bright",
              tone === "warn" && "bg-warning",
              tone === "error" && "bg-error",
              tone === "muted" && "bg-muted-foreground/50",
            )}
          />
          <span className="truncate">
            {t(labelKey, { defaultValue: fallback })}
          </span>
        </div>
      </div>
    </div>
  )
}

function resolveHealth(health: ReturnType<typeof useHealth>): {
  tone: "ok" | "warn" | "error" | "muted"
  labelKey: string
  fallback: string
} {
  if (health.isLoading && !health.data) {
    return { tone: "muted", labelKey: "app.status.checking", fallback: "Checking…" }
  }
  if (health.isError) {
    return { tone: "error", labelKey: "app.status.offline", fallback: "Offline" }
  }
  const status = health.data?.status
  if (status === "UP") {
    return { tone: "ok", labelKey: "app.status.running", fallback: "Running" }
  }
  if (status === "DOWN") {
    return { tone: "error", labelKey: "app.status.down", fallback: "Down" }
  }
  return { tone: "warn", labelKey: "app.status.degraded", fallback: status ?? "Unknown" }
}

function SidebarSectionLabel({ children }: { children: ReactNode }) {
  return (
    <div className="mt-5 px-6 pb-2 font-mono text-[11px] font-semibold uppercase leading-tight tracking-[0.12em] text-muted-foreground">
      {children}
    </div>
  )
}

type TFunc = (key: string, opts?: { defaultValue?: string }) => string

function SidebarNavLink({ item, t }: { item: NavItem; t: TFunc }) {
  return (
    <li>
      <Link
        to={item.to}
        className={cn(
          "group flex w-full items-center gap-3 rounded-md px-3.5 py-[10px] text-[15px] font-normal leading-tight text-sidebar-foreground transition-colors duration-150",
          "hover:bg-surface-hover hover:text-foreground",
        )}
        activeProps={{
          className:
            "bg-surface-selection text-foreground font-medium [&_[data-nav-dot]]:bg-accent-strong [&_[data-nav-dot]]:opacity-100",
        }}
      >
        <item.icon
          aria-hidden="true"
          width={16}
          height={16}
          strokeWidth={1.5}
          className="shrink-0 opacity-70 group-hover:opacity-100"
        />
        <span className="flex-1 truncate">
          {t(item.labelKey, { defaultValue: item.fallback })}
        </span>
      </Link>
    </li>
  )
}

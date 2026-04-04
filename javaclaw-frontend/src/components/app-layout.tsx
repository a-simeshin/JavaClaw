import { IconDeviceDesktop } from "@tabler/icons-react"
import type { ReactNode } from "react"
import { useTranslation } from "react-i18next"

import { AppHeader } from "@/components/app-header"
import { AppSidebar } from "@/components/app-sidebar"

interface AppLayoutProps {
  children: ReactNode
}

/**
 * Shell grid: 256px nav + 56px topbar + content. Per design ctx v3 §6.1.
 * Below 1024px the shell is replaced with a viewport warning — the product
 * is desktop-only (dense tables, logs, multi-panel) and mobile/tablet layouts
 * would require a distinct IA that doesn't exist yet.
 */
export function AppLayout({ children }: AppLayoutProps) {
  return (
    <>
      <ViewportGuard />
      <div
        className="hidden h-full w-full bg-background text-foreground lg:grid"
        style={{
          gridTemplateColumns: "256px 1fr",
          gridTemplateRows: "56px 1fr",
        }}
      >
        <div
          className="row-span-2 border-r border-border bg-sidebar"
          style={{ gridColumn: "1", gridRow: "1 / span 2" }}
        >
          <AppSidebar />
        </div>
        <div
          className="border-b border-border bg-background"
          style={{ gridColumn: "2", gridRow: "1" }}
        >
          <AppHeader />
        </div>
        <main
          className="min-h-0 overflow-auto"
          style={{ gridColumn: "2", gridRow: "2" }}
        >
          {children}
        </main>
      </div>
    </>
  )
}

function ViewportGuard() {
  const { t } = useTranslation()
  return (
    <div
      role="alert"
      aria-live="polite"
      className="flex h-full w-full items-center justify-center bg-background px-6 py-10 text-foreground lg:hidden"
    >
      <div className="flex max-w-sm flex-col items-start gap-4 border-l-2 border-l-separator pl-5">
        <IconDeviceDesktop
          width={28}
          height={28}
          strokeWidth={1.5}
          aria-hidden
          className="text-muted-foreground"
        />
        <div className="flex flex-col gap-2">
          <h1 className="font-mono text-[16px] font-bold leading-tight tracking-[-0.02em] text-foreground">
            {t("viewport.title", { defaultValue: "Desktop required" })}
          </h1>
          <p className="text-[15px] leading-[1.5] text-secondary-foreground">
            {t("viewport.message", {
              defaultValue:
                "JavaClaw is a dense operations console built for desktop (≥1024px). Please open this URL on a larger screen.",
            })}
          </p>
        </div>
        <span className="font-mono text-[10px] tracking-[0.05em] uppercase text-muted-foreground tabular-nums">
          {t("viewport.minWidth", { defaultValue: "min 1024 × 768" })}
        </span>
      </div>
    </div>
  )
}

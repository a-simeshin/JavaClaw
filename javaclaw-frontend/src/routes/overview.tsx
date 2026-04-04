import {
  IconActivity,
  IconBoltFilled,
  IconHeartRateMonitor,
  IconMessage,
} from "@tabler/icons-react"
import { createFileRoute, Link } from "@tanstack/react-router"

import { useHealth, useInfo } from "@/api/config"
import { useSkills } from "@/api/skills"
import { StatCard } from "@/components/overview/stat-card"
import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export const Route = createFileRoute("/overview")({
  component: OverviewPage,
})

function OverviewPage() {
  const health = useHealth()
  const info = useInfo()
  const skills = useSkills()

  const healthStatus = health.data?.status ?? "—"
  const healthTone: "ok" | "warn" | "error" | "default" =
    healthStatus === "UP"
      ? "ok"
      : healthStatus === "DOWN"
        ? "error"
        : healthStatus === "—"
          ? "default"
          : "warn"

  const skillsCount = skills.data?.length ?? 0
  const enabledSkills = skills.data?.filter((s) => s.enabled).length ?? 0

  const javaVersion =
    (info.data?.java as { version?: string } | undefined)?.version ?? "—"
  const appVersion =
    (info.data?.build as { version?: string } | undefined)?.version ??
    (info.data?.app as { version?: string } | undefined)?.version ??
    "—"

  const components = health.data?.components ?? {}

  return (
    <div className="flex flex-col gap-6 p-6">
      <PageHeader
        title="Overview"
        subtitle="System state at a glance"
      />

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
        <StatCard
          label="Health"
          value={healthStatus}
          tone={healthTone}
          icon={IconHeartRateMonitor}
          hint={
            health.isLoading
              ? "checking…"
              : `${Object.keys(components).length} components`
          }
        />
        <StatCard
          label="Connection"
          value={health.isError ? "Offline" : "Online"}
          tone={health.isError ? "error" : "ok"}
          icon={IconActivity}
        />
        <StatCard
          label="Skills"
          value={skillsCount}
          hint={`${enabledSkills} enabled`}
          icon={IconBoltFilled}
        />
        <StatCard
          label="Conversations"
          value="—"
          hint="active"
          icon={IconMessage}
        />
      </div>

      <section className="grid grid-cols-1 gap-4 lg:grid-cols-3">
        <div className="rounded-md border border-border bg-card p-4 lg:col-span-2">
          <h2 className="mb-3 font-mono text-[10px] font-semibold uppercase leading-tight tracking-[0.12em] text-muted-foreground">
            System info
          </h2>
          <dl className="grid grid-cols-2 gap-x-6 gap-y-2 text-[13px]">
            <div className="flex items-center justify-between">
              <dt className="text-muted-foreground">Java</dt>
              <dd className="font-mono text-[12px]">{javaVersion}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-muted-foreground">Version</dt>
              <dd className="font-mono text-[12px]">{appVersion}</dd>
            </div>
            {Object.entries(components).map(([name, comp]) => (
              <div
                key={name}
                className="flex items-center justify-between border-t border-border/60 pt-2"
              >
                <dt className="text-muted-foreground">{name}</dt>
                <dd
                  className={cn(
                    "font-mono text-[12px] font-medium",
                    comp.status === "UP" ? "text-success" : "text-error",
                  )}
                >
                  {comp.status}
                </dd>
              </div>
            ))}
          </dl>
        </div>

        <div className="rounded-md border border-border bg-card p-4">
          <h2 className="mb-3 font-mono text-[10px] font-semibold uppercase leading-tight tracking-[0.12em] text-muted-foreground">
            Quick actions
          </h2>
          <div className="flex flex-col gap-2">
            <Button asChild variant="outline" size="sm" className="justify-start">
              <Link to="/chat">Open chat</Link>
            </Button>
            <Button asChild variant="outline" size="sm" className="justify-start">
              <Link to="/admin/skills">Manage skills</Link>
            </Button>
            <Button asChild variant="outline" size="sm" className="justify-start">
              <Link to="/logs">View logs</Link>
            </Button>
          </div>
        </div>
      </section>

      <section className="rounded-md border border-border bg-card p-4">
        <h2 className="mb-3 font-mono text-[10px] font-semibold uppercase leading-tight tracking-[0.12em] text-muted-foreground">
          Recent activity
        </h2>
        <p className="text-[12px] text-muted-foreground">
          No recent events.
        </p>
      </section>
    </div>
  )
}

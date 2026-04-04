import { IconPlayerPlay } from "@tabler/icons-react"
import { createFileRoute } from "@tanstack/react-router"
import { toast } from "sonner"

import {
  type CronJobDto,
  useCronJobs,
  useToggleCronJob,
  useTriggerCronJob,
} from "@/api/cron"
import { DataTable, type Column } from "@/components/admin/data-table"
import { StatusDot } from "@/components/admin/status-dot"
import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"
import { Switch } from "@/components/ui/switch"

export const Route = createFileRoute("/cron")({
  component: CronPage,
})

function CronPage() {
  const { data, isLoading } = useCronJobs()
  const toggleMut = useToggleCronJob()
  const triggerMut = useTriggerCronJob()

  const columns: Column<CronJobDto>[] = [
    {
      key: "status",
      header: "",
      width: "24px",
      cell: (r) => (
        <StatusDot tone={r.status === "ok" ? "ok" : r.status === "error" ? "error" : "muted"} />
      ),
    },
    {
      key: "name",
      header: "Name",
      cell: (r) => <span className="font-medium">{r.name}</span>,
    },
    {
      key: "expression",
      header: "Schedule",
      width: "20%",
      cell: (r) => (
        <span className="font-mono text-[12px] text-muted-foreground">
          {r.expression}
        </span>
      ),
    },
    {
      key: "lastRun",
      header: "Last run",
      width: "15%",
      cell: (r) => (
        <span className="text-[12px] text-muted-foreground">
          {r.lastRun ?? "—"}
        </span>
      ),
    },
    {
      key: "enabled",
      header: "Enabled",
      width: "10%",
      cell: (r) => (
        <Switch
          checked={r.enabled}
          onCheckedChange={(v) =>
            toggleMut.mutate(
              { id: r.id, enabled: v },
              {
                onError: (e) =>
                  toast.error(`Failed: ${(e as Error).message}`),
              },
            )
          }
        />
      ),
    },
    {
      key: "actions",
      header: "",
      width: "80px",
      className: "text-right",
      cell: (r) => (
        <Button
          variant="ghost"
          size="icon-xs"
          aria-label="Run now"
          onClick={() =>
            triggerMut.mutate(r.id, {
              onSuccess: () => toast.success("Triggered"),
              onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
            })
          }
        >
          <IconPlayerPlay strokeWidth={1.5} />
        </Button>
      ),
    },
  ]

  return (
    <div className="flex flex-col gap-4 p-6">
      <PageHeader title="Cron" subtitle="Scheduled jobs" />
      <DataTable
        columns={columns}
        rows={data ?? []}
        rowKey={(r) => r.id}
        loading={isLoading}
        empty="No cron jobs configured"
      />
    </div>
  )
}

import { IconChevronRight } from "@tabler/icons-react"
import { createFileRoute } from "@tanstack/react-router"
import { useMemo, useState } from "react"

import { useConfigEnv } from "@/api/config"
import { PageHeader } from "@/components/page-header"
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from "@/components/ui/collapsible"
import { Input } from "@/components/ui/input"
import { cn } from "@/lib/utils"

export const Route = createFileRoute("/config")({
  component: ConfigPage,
})

function ConfigPage() {
  const { data, isLoading, isError } = useConfigEnv()
  const [filter, setFilter] = useState("")

  const sources = useMemo(() => {
    if (!data) return []
    const q = filter.trim().toLowerCase()
    return data.propertySources.map((src) => {
      const entries = Object.entries(src.properties)
      const filtered = q
        ? entries.filter(
            ([k, v]) =>
              k.toLowerCase().includes(q) ||
              String(v.value).toLowerCase().includes(q),
          )
        : entries
      return { name: src.name, entries: filtered }
    })
  }, [data, filter])

  return (
    <div className="flex flex-col gap-4 p-6">
      <PageHeader
        title="Config"
        subtitle="Application configuration (read-only)"
      />
      <Input
        placeholder="Filter keys"
        value={filter}
        onChange={(e) => setFilter(e.target.value)}
        className="max-w-sm"
      />
      {isLoading ? (
        <div className="rounded-md border border-border bg-card p-6 text-[12px] text-muted-foreground">
          Loading…
        </div>
      ) : isError ? (
        <div className="rounded-md border border-border bg-card p-6 text-[12px] text-muted-foreground">
          Config endpoint unavailable (/actuator/env).
        </div>
      ) : (
        <div className="flex flex-col gap-2">
          {sources.map((src) => (
            <Collapsible key={src.name} defaultOpen={src.entries.length > 0 && src.entries.length < 20}>
              <div className="rounded-md border border-border bg-card">
                <CollapsibleTrigger
                  className={cn(
                    "group/trigger flex w-full items-center gap-2 px-3 py-2 text-left text-[13px]",
                    "hover:bg-surface-card transition-colors duration-150",
                  )}
                >
                  <IconChevronRight
                    width={14}
                    height={14}
                    strokeWidth={1.5}
                    className="text-muted-foreground transition-transform group-data-[state=open]/trigger:rotate-90"
                  />
                  <span className="font-mono text-[12px] font-medium text-foreground">
                    {src.name}
                  </span>
                  <span className="ml-auto text-[11px] text-muted-foreground">
                    {src.entries.length}
                  </span>
                </CollapsibleTrigger>
                <CollapsibleContent>
                  <div className="border-t border-border">
                    {src.entries.length === 0 ? (
                      <div className="px-3 py-2 text-[12px] text-muted-foreground">
                        No entries
                      </div>
                    ) : (
                      <dl className="divide-y divide-border/60">
                        {src.entries.map(([k, v]) => (
                          <div
                            key={k}
                            className="grid grid-cols-[1fr_1fr] gap-4 px-3 py-1.5"
                          >
                            <dt className="font-mono text-[12px] text-muted-foreground break-all">
                              {k}
                            </dt>
                            <dd className="font-mono text-[12px] text-foreground break-all">
                              {String(v.value)}
                            </dd>
                          </div>
                        ))}
                      </dl>
                    )}
                  </div>
                </CollapsibleContent>
              </div>
            </Collapsible>
          ))}
        </div>
      )}
    </div>
  )
}

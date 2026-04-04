import { IconSearch } from "@tabler/icons-react"
import { createFileRoute } from "@tanstack/react-router"
import { useEffect, useMemo, useRef, useState } from "react"

import { useLogfile } from "@/api/logs"
import { PageHeader } from "@/components/page-header"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select"
import { Switch } from "@/components/ui/switch"
import { cn } from "@/lib/utils"

export const Route = createFileRoute("/logs")({
  component: LogsPage,
})

type Level = "ALL" | "DEBUG" | "INFO" | "WARN" | "ERROR"

const LEVELS: Level[] = ["ALL", "DEBUG", "INFO", "WARN", "ERROR"]

function lineLevel(line: string): string | null {
  const m = line.match(/\b(DEBUG|INFO|WARN|WARNING|ERROR|TRACE)\b/)
  if (!m) return null
  const l = m[1]
  return l === "WARNING" ? "WARN" : l
}

function LogsPage() {
  const [follow, setFollow] = useState(true)
  const [search, setSearch] = useState("")
  const [level, setLevel] = useState<Level>("ALL")
  const logRef = useRef<HTMLPreElement>(null)

  const { data, isLoading, isError } = useLogfile(follow)

  const filteredLines = useMemo(() => {
    if (!data) return []
    const lines = data.split("\n")
    const q = search.trim().toLowerCase()
    return lines.filter((line) => {
      if (level !== "ALL") {
        const l = lineLevel(line)
        if (l !== level) return false
      }
      if (q && !line.toLowerCase().includes(q)) return false
      return true
    })
  }, [data, search, level])

  useEffect(() => {
    if (follow && logRef.current) {
      logRef.current.scrollTop = logRef.current.scrollHeight
    }
  }, [filteredLines, follow])

  return (
    <div className="flex h-full flex-col gap-3 p-6">
      <PageHeader title="Logs" subtitle="Application log stream" />
      <div className="flex flex-wrap items-center gap-3">
        <div className="relative flex-1 min-w-[220px] max-w-sm">
          <IconSearch
            className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            strokeWidth={1.5}
          />
          <Input
            placeholder="Search logs"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-8"
          />
        </div>
        <div className="flex items-center gap-2">
          <Label htmlFor="log-level" className="text-[12px] text-muted-foreground">
            Level
          </Label>
          <Select value={level} onValueChange={(v) => setLevel(v as Level)}>
            <SelectTrigger id="log-level" className="w-[120px]">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {LEVELS.map((l) => (
                <SelectItem key={l} value={l}>
                  {l}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center gap-2">
          <Label htmlFor="log-follow" className="text-[12px] text-muted-foreground">
            Follow
          </Label>
          <Switch id="log-follow" checked={follow} onCheckedChange={setFollow} />
        </div>
      </div>
      <pre
        ref={logRef}
        className={cn(
          "min-h-0 flex-1 overflow-auto rounded-md border border-border bg-card p-3",
          "font-mono text-[12px] leading-relaxed text-foreground/90 whitespace-pre-wrap break-all",
        )}
      >
        {isLoading
          ? "Loading…"
          : isError
            ? "Logfile endpoint unavailable (/actuator/logfile)."
            : filteredLines.length === 0
              ? "No log lines match filters."
              : filteredLines.join("\n")}
      </pre>
    </div>
  )
}

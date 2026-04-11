import {
  IconPencil,
  IconPlus,
  IconRefresh,
  IconTool,
  IconTrash,
} from "@tabler/icons-react"
import { createFileRoute } from "@tanstack/react-router"
import { useState } from "react"
import { toast } from "sonner"

import {
  type McpServerDto,
  type McpStatusDto,
  type McpType,
  useCreateMcpServer,
  useDeleteMcpServer,
  useMcpServers,
  useMcpStatus,
  useMcpTools,
  useUpdateMcpServer,
} from "@/api/mcp"
import { ConfirmDialog } from "@/components/admin/confirm-dialog"
import { DataTable, type Column } from "@/components/admin/data-table"
import { StatusDot } from "@/components/admin/status-dot"
import { Badge } from "@/components/ui/badge"
import { Button } from "@/components/ui/button"
import {
  Dialog,
  DialogContent,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog"
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
import { Textarea } from "@/components/ui/textarea"

export const Route = createFileRoute("/admin/mcp")({
  component: McpPage,
})

function statusTone(status?: string): "ok" | "error" | "muted" {
  if (status === "connected") return "ok"
  if (status === "error") return "error"
  return "muted"
}

function statusLabel(status?: string): string {
  if (status === "connected") return "Connected"
  if (status === "error") return "Error"
  if (status === "disabled") return "Disabled"
  return "Unknown"
}

function formatCheckedAt(iso?: string): string {
  if (!iso) return ""
  try {
    return new Date(iso).toLocaleString()
  } catch {
    return iso
  }
}

function ServerHealthDetail({ id }: { id: string }) {
  const { data } = useMcpStatus(id)
  if (!data) return null
  return (
    <div className="flex items-center gap-2 text-[11px] text-muted-foreground">
      <StatusDot tone={statusTone(data.status)} />
      <span>{statusLabel(data.status)}</span>
      {data.detail && (
        <span className="font-mono text-[10px]">{data.detail}</span>
      )}
      {data.checkedAt && (
        <span className="ml-auto text-[10px]">
          {formatCheckedAt(data.checkedAt)}
        </span>
      )}
    </div>
  )
}

function McpPage() {
  const { data, isLoading } = useMcpServers()
  const { data: toolsData, refetch: refetchTools } = useMcpTools()
  const [editing, setEditing] = useState<McpServerDto | null>(null)
  const [creating, setCreating] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const [expandedId, setExpandedId] = useState<string | null>(null)

  const createMut = useCreateMcpServer()
  const updateMut = useUpdateMcpServer()
  const deleteMut = useDeleteMcpServer()

  const handleToggle = (srv: McpServerDto) => {
    if (!srv.id) return
    updateMut.mutate(
      { id: srv.id, body: { ...srv, enabled: !srv.enabled } },
      {
        onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
      },
    )
  }

  const columns: Column<McpServerDto>[] = [
    {
      key: "status",
      header: "",
      width: "24px",
      cell: (r) => (
        <StatusDot tone={statusTone(r.status)} />
      ),
    },
    {
      key: "name",
      header: "Name",
      cell: (r) => (
        <button
          type="button"
          className="font-medium hover:underline"
          onClick={() =>
            setExpandedId(expandedId === r.id ? null : (r.id ?? null))
          }
        >
          {r.name}
        </button>
      ),
      width: "25%",
    },
    {
      key: "type",
      header: "Type",
      width: "12%",
      cell: (r) => (
        <Badge variant="outline" className="font-mono text-[11px]">
          {r.type}
        </Badge>
      ),
    },
    {
      key: "config",
      header: "Config",
      cell: (r) => (
        <span className="line-clamp-1 font-mono text-[12px] text-muted-foreground">
          {r.config}
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
          onCheckedChange={() => handleToggle(r)}
          aria-label={`Toggle ${r.name}`}
        />
      ),
    },
    {
      key: "actions",
      header: "",
      width: "100px",
      className: "text-right",
      cell: (r) => (
        <div className="flex justify-end gap-1">
          <Button
            variant="ghost"
            size="icon-xs"
            onClick={() => setEditing(r)}
            aria-label="Edit"
          >
            <IconPencil strokeWidth={1.5} />
          </Button>
          <Button
            variant="ghost"
            size="icon-xs"
            onClick={() => setDeleteId(r.id ?? null)}
            aria-label="Delete"
          >
            <IconTrash strokeWidth={1.5} />
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="flex flex-col gap-4 p-6">
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <IconTool size={16} className="text-muted-foreground" strokeWidth={1.5} />
          <span className="text-[13px] text-muted-foreground">
            {toolsData ? `${toolsData.count} tools cached` : "Loading tools\u2026"}
          </span>
          <Button
            variant="ghost"
            size="icon-xs"
            onClick={() => refetchTools()}
            aria-label="Refresh tools"
          >
            <IconRefresh size={14} strokeWidth={1.5} />
          </Button>
        </div>
        <Button onClick={() => setCreating(true)} size="sm">
          <IconPlus strokeWidth={1.5} />
          Add server
        </Button>
      </div>

      {toolsData && toolsData.count > 0 && (
        <div className="flex flex-wrap gap-1">
          {toolsData.toolNames.map((name) => (
            <Badge
              key={name}
              variant="secondary"
              className="font-mono text-[10px]"
            >
              {name}
            </Badge>
          ))}
        </div>
      )}

      <DataTable
        columns={columns}
        rows={data ?? []}
        rowKey={(r) => r.id ?? r.name}
        loading={isLoading}
        empty="No MCP servers configured"
      />

      {expandedId && (
        <div className="rounded-md border border-border bg-muted/30 px-4 py-3">
          <ServerHealthDetail id={expandedId} />
        </div>
      )}

      <McpDialog
        open={creating}
        onOpenChange={setCreating}
        title="Add MCP server"
        initial={{ name: "", type: "stdio", config: "", enabled: true }}
        onSubmit={(body) =>
          createMut.mutate(body, {
            onSuccess: () => {
              toast.success("Server added")
              setCreating(false)
            },
            onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
          })
        }
        pending={createMut.isPending}
      />

      <McpDialog
        open={editing !== null}
        onOpenChange={(o) => !o && setEditing(null)}
        title="Edit MCP server"
        initial={
          editing ?? { name: "", type: "stdio", config: "", enabled: true }
        }
        onSubmit={(body) => {
          if (!editing?.id) return
          updateMut.mutate(
            { id: editing.id, body: { ...editing, ...body } },
            {
              onSuccess: () => {
                toast.success("Server updated")
                setEditing(null)
              },
              onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
            },
          )
        }}
        pending={updateMut.isPending}
      />

      <ConfirmDialog
        open={deleteId !== null}
        onOpenChange={(o) => !o && setDeleteId(null)}
        title="Delete server?"
        description="This action cannot be undone."
        confirmLabel="Delete"
        destructive
        pending={deleteMut.isPending}
        onConfirm={() => {
          if (!deleteId) return
          deleteMut.mutate(deleteId, {
            onSuccess: () => {
              toast.success("Server deleted")
              setDeleteId(null)
            },
            onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
          })
        }}
      />
    </div>
  )
}

interface McpDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  initial: McpServerDto
  onSubmit: (body: McpServerDto) => void
  pending: boolean
}

function McpDialog({
  open,
  onOpenChange,
  title,
  initial,
  onSubmit,
  pending,
}: McpDialogProps) {
  const [name, setName] = useState(initial.name)
  const [type, setType] = useState<McpType>(initial.type)
  const [config, setConfig] = useState(initial.config)
  const [enabled, setEnabled] = useState(initial.enabled)

  const handleOpenChange = (o: boolean) => {
    if (o) {
      setName(initial.name)
      setType(initial.type)
      setConfig(initial.config)
      setEnabled(initial.enabled)
    }
    onOpenChange(o)
  }

  return (
    <Dialog open={open} onOpenChange={handleOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>{title}</DialogTitle>
        </DialogHeader>
        <div className="flex flex-col gap-4">
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="mcp-name">Name</Label>
            <Input
              id="mcp-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="mcp-type">Type</Label>
            <Select
              value={type}
              onValueChange={(v) => setType(v as McpType)}
            >
              <SelectTrigger id="mcp-type">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value="stdio">stdio</SelectItem>
                <SelectItem value="http">http</SelectItem>
              </SelectContent>
            </Select>
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="mcp-config">
              {type === "stdio"
                ? "Command (e.g. npx @scope/server)"
                : "URL (e.g. https://host/mcp)"}
            </Label>
            <Textarea
              id="mcp-config"
              value={config}
              onChange={(e) => setConfig(e.target.value)}
              rows={3}
              className="font-mono text-[12px]"
            />
          </div>
          <div className="flex items-center justify-between">
            <Label htmlFor="mcp-enabled">Enabled</Label>
            <Switch
              id="mcp-enabled"
              checked={enabled}
              onCheckedChange={setEnabled}
            />
          </div>
        </div>
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button
            onClick={() => onSubmit({ name, type, config, enabled })}
            disabled={pending || !name.trim()}
          >
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

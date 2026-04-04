import { IconPencil, IconPlus, IconSearch, IconTrash } from "@tabler/icons-react"
import { createFileRoute } from "@tanstack/react-router"
import { useMemo, useState } from "react"
import { toast } from "sonner"

import {
  type SkillDto,
  useCreateSkill,
  useDeleteSkill,
  useSkills,
  useUpdateSkill,
} from "@/api/skills"
import { ConfirmDialog } from "@/components/admin/confirm-dialog"
import { DataTable, type Column } from "@/components/admin/data-table"
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
import { Switch } from "@/components/ui/switch"
import { Textarea } from "@/components/ui/textarea"

export const Route = createFileRoute("/admin/skills")({
  component: SkillsPage,
})

function SkillsPage() {
  const { data, isLoading } = useSkills()
  const [search, setSearch] = useState("")
  const [editing, setEditing] = useState<SkillDto | null>(null)
  const [creating, setCreating] = useState(false)
  const [deleteId, setDeleteId] = useState<string | null>(null)

  const createMut = useCreateSkill()
  const updateMut = useUpdateSkill()
  const deleteMut = useDeleteSkill()

  const rows = useMemo(() => {
    const list = data ?? []
    if (!search.trim()) return list
    const q = search.toLowerCase()
    return list.filter(
      (s) =>
        s.name.toLowerCase().includes(q) ||
        s.description.toLowerCase().includes(q),
    )
  }, [data, search])

  const handleToggle = (skill: SkillDto) => {
    if (!skill.id) return
    updateMut.mutate(
      { id: skill.id, body: { ...skill, enabled: !skill.enabled } },
      {
        onError: (e) => toast.error(`Failed to update: ${(e as Error).message}`),
      },
    )
  }

  const handleDelete = () => {
    if (!deleteId) return
    deleteMut.mutate(deleteId, {
      onSuccess: () => {
        toast.success("Skill deleted")
        setDeleteId(null)
      },
      onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
    })
  }

  const columns: Column<SkillDto>[] = [
    {
      key: "name",
      header: "Name",
      cell: (r) => <span className="font-medium">{r.name}</span>,
      width: "22%",
    },
    {
      key: "description",
      header: "Description",
      cell: (r) => (
        <span className="line-clamp-1 text-muted-foreground">
          {r.description}
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
      <div className="flex items-center gap-3">
        <div className="relative flex-1 max-w-sm">
          <IconSearch
            className="absolute left-2.5 top-1/2 size-4 -translate-y-1/2 text-muted-foreground"
            strokeWidth={1.5}
          />
          <Input
            placeholder="Search skills"
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="pl-8"
          />
        </div>
        <Button onClick={() => setCreating(true)} size="sm">
          <IconPlus strokeWidth={1.5} />
          New skill
        </Button>
      </div>
      <DataTable
        columns={columns}
        rows={rows}
        rowKey={(r) => r.id ?? r.name}
        loading={isLoading}
        empty="No skills configured"
      />

      <SkillDialog
        open={creating}
        onOpenChange={setCreating}
        title="New skill"
        initial={{ name: "", description: "", enabled: true }}
        onSubmit={(body) =>
          createMut.mutate(body, {
            onSuccess: () => {
              toast.success("Skill created")
              setCreating(false)
            },
            onError: (e) =>
              toast.error(`Failed: ${(e as Error).message}`),
          })
        }
        pending={createMut.isPending}
      />

      <SkillDialog
        open={editing !== null}
        onOpenChange={(o) => !o && setEditing(null)}
        title="Edit skill"
        initial={
          editing ?? { name: "", description: "", enabled: true }
        }
        onSubmit={(body) => {
          if (!editing?.id) return
          updateMut.mutate(
            { id: editing.id, body: { ...editing, ...body } },
            {
              onSuccess: () => {
                toast.success("Skill updated")
                setEditing(null)
              },
              onError: (e) =>
                toast.error(`Failed: ${(e as Error).message}`),
            },
          )
        }}
        pending={updateMut.isPending}
      />

      <ConfirmDialog
        open={deleteId !== null}
        onOpenChange={(o) => !o && setDeleteId(null)}
        title="Delete skill?"
        description="This action cannot be undone."
        confirmLabel="Delete"
        destructive
        pending={deleteMut.isPending}
        onConfirm={handleDelete}
      />
    </div>
  )
}

interface SkillDialogProps {
  open: boolean
  onOpenChange: (open: boolean) => void
  title: string
  initial: SkillDto
  onSubmit: (body: SkillDto) => void
  pending: boolean
}

function SkillDialog({
  open,
  onOpenChange,
  title,
  initial,
  onSubmit,
  pending,
}: SkillDialogProps) {
  const [name, setName] = useState(initial.name)
  const [description, setDescription] = useState(initial.description)
  const [enabled, setEnabled] = useState(initial.enabled)

  // Reset state when dialog opens
  const handleOpenChange = (o: boolean) => {
    if (o) {
      setName(initial.name)
      setDescription(initial.description)
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
            <Label htmlFor="skill-name">Name</Label>
            <Input
              id="skill-name"
              value={name}
              onChange={(e) => setName(e.target.value)}
            />
          </div>
          <div className="flex flex-col gap-1.5">
            <Label htmlFor="skill-desc">Description</Label>
            <Textarea
              id="skill-desc"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              rows={4}
            />
          </div>
          <div className="flex items-center justify-between">
            <Label htmlFor="skill-enabled">Enabled</Label>
            <Switch
              id="skill-enabled"
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
            onClick={() => onSubmit({ name, description, enabled })}
            disabled={pending || !name.trim()}
          >
            Save
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  )
}

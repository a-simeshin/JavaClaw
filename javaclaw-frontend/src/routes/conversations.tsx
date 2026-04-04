import { IconExternalLink, IconTrash } from "@tabler/icons-react"
import { createFileRoute, Link } from "@tanstack/react-router"
import { useState } from "react"
import { toast } from "sonner"

import {
  type AdminConversationDto,
  useAdminConversations,
  useDeleteAdminConversation,
} from "@/api/admin-conversations"
import { ConfirmDialog } from "@/components/admin/confirm-dialog"
import { DataTable, type Column } from "@/components/admin/data-table"
import { PageHeader } from "@/components/page-header"
import { Button } from "@/components/ui/button"

export const Route = createFileRoute("/conversations")({
  component: AdminConversationsPage,
})

const PAGE_SIZE = 20

function formatDate(iso?: string): string {
  if (!iso) return "—"
  const d = new Date(iso)
  if (Number.isNaN(d.getTime())) return iso
  return d.toLocaleString()
}

function AdminConversationsPage() {
  const [page, setPage] = useState(0)
  const { data, isLoading } = useAdminConversations(page, PAGE_SIZE)
  const [deleteId, setDeleteId] = useState<string | null>(null)
  const deleteMut = useDeleteAdminConversation()

  const total = data?.total ?? 0
  const pages = Math.max(1, Math.ceil(total / PAGE_SIZE))

  const columns: Column<AdminConversationDto>[] = [
    {
      key: "title",
      header: "Title",
      cell: (r) => <span className="font-medium">{r.title || "(untitled)"}</span>,
    },
    {
      key: "owner",
      header: "Owner",
      width: "15%",
      cell: (r) => (
        <span className="font-mono text-[12px] text-muted-foreground">
          {r.owner}
        </span>
      ),
    },
    {
      key: "messages",
      header: "Messages",
      width: "100px",
      cell: (r) => <span className="tabular-nums">{r.messageCount}</span>,
    },
    {
      key: "created",
      header: "Created",
      width: "15%",
      cell: (r) => (
        <span className="text-[12px] text-muted-foreground">
          {formatDate(r.createdAt)}
        </span>
      ),
    },
    {
      key: "updated",
      header: "Last activity",
      width: "15%",
      cell: (r) => (
        <span className="text-[12px] text-muted-foreground">
          {formatDate(r.updatedAt)}
        </span>
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
            asChild
            variant="ghost"
            size="icon-xs"
            aria-label="Open conversation"
          >
            <Link to="/chat" search={{ conversationId: r.id } as never}>
              <IconExternalLink strokeWidth={1.5} />
            </Link>
          </Button>
          <Button
            variant="ghost"
            size="icon-xs"
            aria-label="Delete"
            onClick={() => setDeleteId(r.id)}
          >
            <IconTrash strokeWidth={1.5} />
          </Button>
        </div>
      ),
    },
  ]

  return (
    <div className="flex flex-col gap-4 p-6">
      <PageHeader
        title="Conversations"
        subtitle="All conversations across the platform"
      />
      <DataTable
        columns={columns}
        rows={data?.items ?? []}
        rowKey={(r) => r.id}
        loading={isLoading}
        empty="No conversations"
      />
      <div className="flex items-center justify-between text-[12px] text-muted-foreground">
        <span>
          {total} total · page {page + 1} / {pages}
        </span>
        <div className="flex gap-1">
          <Button
            variant="outline"
            size="xs"
            disabled={page === 0}
            onClick={() => setPage((p) => Math.max(0, p - 1))}
          >
            Prev
          </Button>
          <Button
            variant="outline"
            size="xs"
            disabled={page >= pages - 1}
            onClick={() => setPage((p) => p + 1)}
          >
            Next
          </Button>
        </div>
      </div>

      <ConfirmDialog
        open={deleteId !== null}
        onOpenChange={(o) => !o && setDeleteId(null)}
        title="Delete conversation?"
        description="All messages will be permanently removed."
        destructive
        confirmLabel="Delete"
        pending={deleteMut.isPending}
        onConfirm={() => {
          if (!deleteId) return
          deleteMut.mutate(deleteId, {
            onSuccess: () => {
              toast.success("Conversation deleted")
              setDeleteId(null)
            },
            onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
          })
        }}
      />
    </div>
  )
}

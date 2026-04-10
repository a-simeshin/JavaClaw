import {
  IconChevronDown,
  IconChevronRight,
  IconFile,
  IconFolder,
  IconFolderOpen,
  IconPlus,
  IconTrash,
} from "@tabler/icons-react"
import { createFileRoute } from "@tanstack/react-router"
import { useCallback, useEffect, useMemo, useState } from "react"
import { toast } from "sonner"

import {
  type FileNode,
  useCreateFile,
  useDeleteFile,
  useFileContent,
  useFileTree,
  useSaveFile,
} from "@/api/files"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export const Route = createFileRoute("/files")({
  component: FilesPage,
})

function FilesPage() {
  const { data: tree, isLoading: treeLoading } = useFileTree()
  const [selectedPath, setSelectedPath] = useState<string | null>(null)
  const [expanded, setExpanded] = useState<Set<string>>(new Set())
  const [showNewFile, setShowNewFile] = useState(false)
  const [newFileName, setNewFileName] = useState("")
  const createFile = useCreateFile()
  const deleteFile = useDeleteFile()

  const toggleExpand = useCallback((path: string) => {
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(path)) next.delete(path)
      else next.add(path)
      return next
    })
  }, [])

  const handleCreate = () => {
    const name = newFileName.trim()
    if (!name) return
    createFile.mutate(
      { path: name },
      {
        onSuccess: () => {
          toast.success(`Created ${name}`)
          setNewFileName("")
          setShowNewFile(false)
          setSelectedPath(name)
        },
        onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
      },
    )
  }

  const handleDelete = (path: string) => {
    deleteFile.mutate(path, {
      onSuccess: () => {
        toast.success(`Deleted ${path}`)
        if (selectedPath === path) setSelectedPath(null)
      },
      onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
    })
  }

  return (
    <div className="flex h-full min-h-0">
      {/* Sidebar — file tree */}
      <div className="flex w-64 shrink-0 flex-col border-r border-border bg-background">
        <div className="flex items-center justify-between border-b border-border px-3 py-2">
          <span className="font-mono text-[12px] font-semibold uppercase tracking-wider text-muted-foreground">
            Files
          </span>
          <button
            onClick={() => setShowNewFile((v) => !v)}
            className="rounded p-1 text-muted-foreground hover:bg-muted hover:text-foreground"
            title="New file"
          >
            <IconPlus size={14} strokeWidth={1.5} />
          </button>
        </div>

        {showNewFile && (
          <div className="flex items-center gap-1 border-b border-border px-2 py-1.5">
            <input
              autoFocus
              value={newFileName}
              onChange={(e) => setNewFileName(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") handleCreate()
                if (e.key === "Escape") setShowNewFile(false)
              }}
              placeholder="path/to/file.md"
              className="flex-1 rounded border border-border bg-card px-2 py-1 font-mono text-[12px] text-foreground outline-none placeholder:text-muted-foreground focus:border-accent"
            />
            <Button
              size="sm"
              variant="ghost"
              onClick={handleCreate}
              disabled={!newFileName.trim() || createFile.isPending}
              className="h-7 px-2 text-[11px]"
            >
              OK
            </Button>
          </div>
        )}

        <div className="min-h-0 flex-1 overflow-y-auto py-1">
          {treeLoading && (
            <div className="px-4 py-6 text-center text-[12px] text-muted-foreground">
              Loading...
            </div>
          )}
          {tree && (
            <TreeNode
              node={tree}
              depth={0}
              selectedPath={selectedPath}
              expanded={expanded}
              onSelect={setSelectedPath}
              onToggle={toggleExpand}
              onDelete={handleDelete}
              isRoot
            />
          )}
        </div>
      </div>

      {/* Main — file editor */}
      <div className="flex min-w-0 flex-1 flex-col">
        {selectedPath ? (
          <FileEditor path={selectedPath} />
        ) : (
          <div className="flex flex-1 items-center justify-center text-muted-foreground">
            <div className="text-center">
              <IconFile size={40} strokeWidth={1} className="mx-auto mb-3 opacity-40" />
              <p className="text-[14px]">Select a file to edit</p>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}

function TreeNode({
  node,
  depth,
  selectedPath,
  expanded,
  onSelect,
  onToggle,
  onDelete,
  isRoot,
}: {
  node: FileNode
  depth: number
  selectedPath: string | null
  expanded: Set<string>
  onSelect: (path: string) => void
  onToggle: (path: string) => void
  onDelete: (path: string) => void
  isRoot?: boolean
}) {
  const isDir = node.type === "dir"
  const isOpen = expanded.has(node.path)
  const isSelected = selectedPath === node.path

  if (isDir) {
    const children = node.children ?? []
    const sorted = useMemo(
      () =>
        [...children].sort((a, b) => {
          if (a.type !== b.type) return a.type === "dir" ? -1 : 1
          return a.name.localeCompare(b.name)
        }),
      [children],
    )

    // Root dir: just render children
    if (isRoot) {
      return (
        <>
          {sorted.map((child) => (
            <TreeNode
              key={child.path}
              node={child}
              depth={depth}
              selectedPath={selectedPath}
              expanded={expanded}
              onSelect={onSelect}
              onToggle={onToggle}
              onDelete={onDelete}
            />
          ))}
        </>
      )
    }

    return (
      <>
        <button
          onClick={() => onToggle(node.path)}
          className="group flex w-full items-center gap-1 px-2 py-[3px] text-left text-[13px] hover:bg-muted"
          style={{ paddingLeft: `${depth * 16 + 8}px` }}
        >
          {isOpen ? (
            <IconChevronDown size={14} strokeWidth={1.5} className="shrink-0 text-muted-foreground" />
          ) : (
            <IconChevronRight size={14} strokeWidth={1.5} className="shrink-0 text-muted-foreground" />
          )}
          {isOpen ? (
            <IconFolderOpen size={14} strokeWidth={1.5} className="shrink-0 text-accent" />
          ) : (
            <IconFolder size={14} strokeWidth={1.5} className="shrink-0 text-accent" />
          )}
          <span className="truncate font-mono text-[12px]">{node.name}</span>
        </button>
        {isOpen &&
          sorted.map((child) => (
            <TreeNode
              key={child.path}
              node={child}
              depth={depth + 1}
              selectedPath={selectedPath}
              expanded={expanded}
              onSelect={onSelect}
              onToggle={onToggle}
              onDelete={onDelete}
            />
          ))}
      </>
    )
  }

  // File node
  return (
    <button
      onClick={() => onSelect(node.path)}
      className={cn(
        "group flex w-full items-center gap-1 px-2 py-[3px] text-left text-[13px] hover:bg-muted",
        isSelected && "bg-surface-selection text-foreground",
      )}
      style={{ paddingLeft: `${depth * 16 + 8}px` }}
    >
      <span className="w-[14px] shrink-0" />
      <IconFile size={14} strokeWidth={1.5} className="shrink-0 text-muted-foreground" />
      <span className="flex-1 truncate font-mono text-[12px]">{node.name}</span>
      <button
        onClick={(e) => {
          e.stopPropagation()
          onDelete(node.path)
        }}
        className="shrink-0 rounded p-0.5 text-muted-foreground opacity-0 hover:bg-error/10 hover:text-error group-hover:opacity-100"
        title={`Delete ${node.name}`}
      >
        <IconTrash size={12} strokeWidth={1.5} />
      </button>
    </button>
  )
}

function FileEditor({ path }: { path: string }) {
  const { data, isLoading } = useFileContent(path)
  const saveMut = useSaveFile()
  const [draft, setDraft] = useState("")
  const [initialized, setInitialized] = useState(false)

  useEffect(() => {
    setInitialized(false)
  }, [path])

  useEffect(() => {
    if (!data) return
    setDraft(data.content)
    setInitialized(true)
  }, [data])

  const dirty = initialized && draft !== (data?.content ?? "")

  const handleSave = () => {
    saveMut.mutate(
      { path, content: draft },
      {
        onSuccess: () => toast.success(`Saved ${path}`),
        onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
      },
    )
  }

  const ext = path.split(".").pop()?.toLowerCase() ?? ""
  const lang = LANG_MAP[ext] ?? ""

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between border-b border-border bg-background px-4 py-2">
        <div className="flex items-center gap-2 min-w-0">
          <span className="truncate font-mono text-[13px] text-foreground">{path}</span>
          {lang && (
            <span className="shrink-0 rounded bg-muted px-1.5 py-0.5 font-mono text-[10px] text-muted-foreground">
              {lang}
            </span>
          )}
          {dirty && (
            <span className="shrink-0 font-mono text-[10px] font-semibold uppercase tracking-[0.05em] text-warning-text">
              unsaved
            </span>
          )}
        </div>
        <div className="flex items-center gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => setDraft(data?.content ?? "")}
            disabled={!dirty}
          >
            Discard
          </Button>
          <Button
            size="sm"
            onClick={handleSave}
            disabled={!dirty || saveMut.isPending}
          >
            Save
          </Button>
        </div>
      </div>

      {isLoading ? (
        <div className="flex flex-1 items-center justify-center text-[13px] text-muted-foreground">
          Loading...
        </div>
      ) : (
        <textarea
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          spellCheck={false}
          onKeyDown={(e) => {
            if ((e.metaKey || e.ctrlKey) && e.key === "s") {
              e.preventDefault()
              if (dirty) handleSave()
            }
          }}
          className={cn(
            "h-full w-full flex-1 resize-none bg-background p-4 font-mono text-[13px] leading-relaxed text-foreground outline-none",
            "placeholder:text-muted-foreground",
          )}
          placeholder="Empty file"
        />
      )}
    </div>
  )
}

const LANG_MAP: Record<string, string> = {
  md: "markdown",
  json: "json",
  yaml: "yaml",
  yml: "yaml",
  js: "javascript",
  ts: "typescript",
  tsx: "tsx",
  jsx: "jsx",
  java: "java",
  py: "python",
  sql: "sql",
  xml: "xml",
  html: "html",
  css: "css",
  txt: "text",
}

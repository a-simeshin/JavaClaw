import { IconEye, IconEyeOff } from "@tabler/icons-react"
import { createFileRoute } from "@tanstack/react-router"
import { useEffect, useMemo, useState } from "react"
import ReactMarkdown from "react-markdown"
import remarkGfm from "remark-gfm"
import { toast } from "sonner"

import { useFileContent, useSaveFile } from "@/api/files"
import { Button } from "@/components/ui/button"
import { cn } from "@/lib/utils"

export const Route = createFileRoute("/admin/prompts")({
  component: PromptsPage,
})

const FILES = ["AGENT.md", "SOUL.md", "INFO.md", "USER_AGENT.md"] as const
type PromptFile = (typeof FILES)[number]

const draftKey = (f: string) => `javaclaw.prompts.draft.${f}`

function PromptsPage() {
  const [active, setActive] = useState<PromptFile>("AGENT.md")
  const [preview, setPreview] = useState(false)
  const { data, isLoading } = useFileContent(active)
  const saveMut = useSaveFile()

  const [draft, setDraft] = useState<string>("")
  const [loaded, setLoaded] = useState(false)

  // Load server content or local draft when active file changes
  useEffect(() => {
    setLoaded(false)
  }, [active])

  useEffect(() => {
    if (!data) return
    const stored = window.localStorage.getItem(draftKey(active))
    setDraft(stored ?? data.content)
    setLoaded(true)
  }, [data, active])

  const serverContent = data?.content ?? ""
  const dirty = useMemo(
    () => loaded && draft !== serverContent,
    [loaded, draft, serverContent],
  )

  const handleChange = (v: string) => {
    setDraft(v)
    window.localStorage.setItem(draftKey(active), v)
  }

  const handleSave = () => {
    saveMut.mutate(
      { path: active, content: draft },
      {
        onSuccess: () => {
          window.localStorage.removeItem(draftKey(active))
          toast.success(`${active} saved`)
        },
        onError: (e) => toast.error(`Failed: ${(e as Error).message}`),
      },
    )
  }

  const handleDiscard = () => {
    setDraft(serverContent)
    window.localStorage.removeItem(draftKey(active))
  }

  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between border-b border-border bg-background px-6 py-2">
        <nav className="flex items-center gap-0.5">
          {FILES.map((f) => (
            <button
              key={f}
              onClick={() => setActive(f)}
              className={cn(
                "rounded-md px-2.5 py-1.5 text-[12px] font-mono transition-colors",
                active === f
                  ? "bg-muted text-foreground"
                  : "text-muted-foreground hover:text-foreground",
              )}
            >
              {f}
            </button>
          ))}
        </nav>
        <div className="flex items-center gap-2">
          {dirty ? (
            <span className="font-mono text-[10px] font-semibold uppercase tracking-[0.05em] text-warning-text">Unsaved changes</span>
          ) : null}
          <Button
            variant="ghost"
            size="sm"
            onClick={() => setPreview((p) => !p)}
          >
            {preview ? (
              <IconEyeOff strokeWidth={1.5} />
            ) : (
              <IconEye strokeWidth={1.5} />
            )}
            {preview ? "Hide preview" : "Preview"}
          </Button>
          <Button
            variant="outline"
            size="sm"
            onClick={handleDiscard}
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

      <div className="grid min-h-0 flex-1 gap-0" style={{ gridTemplateColumns: preview ? "1fr 1fr" : "1fr" }}>
        <div className="min-h-0 overflow-auto border-r border-border">
          <textarea
            value={draft}
            onChange={(e) => handleChange(e.target.value)}
            disabled={isLoading}
            spellCheck={false}
            className={cn(
              "h-full w-full resize-none bg-background p-4 font-mono text-[13px] text-foreground outline-none",
              "placeholder:text-muted-foreground",
            )}
            placeholder={isLoading ? "Loading…" : "# Prompt content"}
          />
        </div>
        {preview ? (
          <div className="min-h-0 overflow-auto bg-card p-4 prose prose-sm dark:prose-invert max-w-none">
            <ReactMarkdown remarkPlugins={[remarkGfm]}>{draft}</ReactMarkdown>
          </div>
        ) : null}
      </div>
    </div>
  )
}

import { cn } from "@/lib/utils"

interface UserMessageProps {
  content: string
  className?: string
}

/**
 * User message — right-aligned pill bubble (Claude / ChatGPT convention).
 * Differentiated from agent by alignment + subtle surface tint. No border.
 */
export function UserMessage({ content, className }: UserMessageProps) {
  if (!content.trim()) return null
  return (
    <div className={cn("flex w-full justify-end", className)}>
      <div
        data-role="user"
        className="max-w-[80%] rounded-2xl rounded-tr-sm bg-surface-elevated px-4 py-2.5 text-[15px] leading-[1.5] text-foreground [overflow-wrap:anywhere] whitespace-pre-wrap"
      >
        {content}
      </div>
    </div>
  )
}

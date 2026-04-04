import { cn } from "@/lib/utils"

type Tone = "ok" | "warn" | "error" | "muted"

const TONE_CLASSES: Record<Tone, string> = {
  ok: "bg-success",
  warn: "bg-warning",
  error: "bg-error",
  muted: "bg-muted-foreground/40",
}

export function StatusDot({
  tone = "muted",
  label,
  className,
}: {
  tone?: Tone
  label?: string
  className?: string
}) {
  return (
    <span
      className={cn("inline-flex items-center gap-2 text-[12px]", className)}
    >
      <span
        aria-hidden="true"
        className={cn("inline-block size-2 rounded-full", TONE_CLASSES[tone])}
      />
      {label ? <span className="text-muted-foreground">{label}</span> : null}
    </span>
  )
}

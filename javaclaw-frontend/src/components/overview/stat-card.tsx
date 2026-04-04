import type { ComponentType, ReactNode, SVGProps } from "react"

import { cn } from "@/lib/utils"

interface StatCardProps {
  label: string
  value: ReactNode
  hint?: ReactNode
  icon?: ComponentType<SVGProps<SVGSVGElement>>
  tone?: "default" | "ok" | "warn" | "error"
}

/**
 * Tone bar uses JavaClaw semantic status tokens so both themes resolve
 * correctly (no raw Tailwind palette — anti-AI-slop §13.1).
 */
const TONE_BAR: Record<NonNullable<StatCardProps["tone"]>, string> = {
  default: "bg-separator",
  ok: "bg-success",
  warn: "bg-warning",
  error: "bg-error",
}

export function StatCard({
  label,
  value,
  hint,
  icon: Icon,
  tone = "default",
}: StatCardProps) {
  return (
    <div className="relative overflow-hidden rounded-sm border border-border bg-card px-4 py-3">
      <span
        aria-hidden="true"
        className={cn("absolute inset-y-0 left-0 w-[2px]", TONE_BAR[tone])}
      />
      <div className="flex items-start justify-between gap-2">
        <div className="flex flex-col gap-1.5">
          <span className="font-mono text-[9px] font-semibold uppercase leading-tight tracking-[0.08em] text-muted-foreground">
            {label}
          </span>
          <span className="font-mono text-[22px] font-bold leading-none tracking-[-0.02em] text-foreground tabular-nums">
            {value}
          </span>
          {hint ? (
            <span className="text-[12px] leading-tight text-muted-foreground">
              {hint}
            </span>
          ) : null}
        </div>
        {Icon ? (
          <Icon
            aria-hidden="true"
            width={16}
            height={16}
            strokeWidth={1.5}
            className="shrink-0 text-muted-foreground"
          />
        ) : null}
      </div>
    </div>
  )
}

interface PageHeaderProps {
  title: string
  subtitle?: string
}

/**
 * Page title per design ctx v3 §5.1:
 * JetBrains Mono 16px/700/-0.02em for tech-audience recognition.
 */
export function PageHeader({ title, subtitle }: PageHeaderProps) {
  return (
    <header className="flex flex-col gap-1.5">
      <h1 className="font-mono text-[16px] font-bold leading-[1.2] tracking-[-0.02em] text-foreground">
        {title}
      </h1>
      {subtitle ? (
        <p className="text-[13px] leading-[1.5] text-muted-foreground">
          {subtitle}
        </p>
      ) : null}
    </header>
  )
}

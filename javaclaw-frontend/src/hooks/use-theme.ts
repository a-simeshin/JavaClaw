import * as React from "react"

export type Theme = "light" | "dark" | "system"

const STORAGE_KEY = "javaclaw.theme"

function resolve(theme: Theme): "light" | "dark" {
  if (theme === "system") {
    return window.matchMedia("(prefers-color-scheme: dark)").matches
      ? "dark"
      : "light"
  }
  return theme
}

function apply(theme: Theme) {
  const resolved = resolve(theme)
  const root = document.documentElement
  // Suppress CSS transitions during theme swap so bg/text/border tokens
  // don't interpolate through mid-grey values (black/white flicker).
  // Window check keeps initTheme (SSR-safe path) from touching requestAnimationFrame.
  if (typeof window !== "undefined") {
    root.classList.add("theme-switching")
  }
  root.classList.toggle("dark", resolved === "dark")
  root.style.colorScheme = resolved
  if (typeof window !== "undefined") {
    // Double rAF guarantees the class change has been committed to the DOM
    // and styles re-applied before we re-enable transitions.
    window.requestAnimationFrame(() => {
      window.requestAnimationFrame(() => {
        root.classList.remove("theme-switching")
      })
    })
  }
}

export function initTheme(): Theme {
  if (typeof window === "undefined") return "dark"
  const stored = window.localStorage.getItem(STORAGE_KEY) as Theme | null
  // Dark-first flagship: default to `dark` on first load.
  const theme: Theme = stored ?? "dark"
  apply(theme)
  return theme
}

export function useTheme() {
  const [theme, setThemeState] = React.useState<Theme>(() => {
    if (typeof window === "undefined") return "dark"
    return (window.localStorage.getItem(STORAGE_KEY) as Theme | null) ?? "dark"
  })

  React.useEffect(() => {
    apply(theme)
    window.localStorage.setItem(STORAGE_KEY, theme)
  }, [theme])

  React.useEffect(() => {
    if (theme !== "system") return
    const mq = window.matchMedia("(prefers-color-scheme: dark)")
    const handler = () => apply("system")
    mq.addEventListener("change", handler)
    return () => mq.removeEventListener("change", handler)
  }, [theme])

  const setTheme = React.useCallback((t: Theme) => setThemeState(t), [])
  const resolved = React.useMemo(() => resolve(theme), [theme])

  return { theme, setTheme, resolved }
}

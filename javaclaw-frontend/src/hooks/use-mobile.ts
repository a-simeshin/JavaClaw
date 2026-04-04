import * as React from "react"

/**
 * OpenClaw breakpoint: <= 1100px collapses sidebar into a mobile sheet.
 */
const MOBILE_BREAKPOINT = 1100

export function useIsMobile(): boolean {
  const [isMobile, setIsMobile] = React.useState<boolean>(() => {
    if (typeof window === "undefined") return false
    return window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT}px)`).matches
  })

  React.useEffect(() => {
    const mq = window.matchMedia(`(max-width: ${MOBILE_BREAKPOINT}px)`)
    const onChange = () => setIsMobile(mq.matches)
    mq.addEventListener("change", onChange)
    return () => mq.removeEventListener("change", onChange)
  }, [])

  return isMobile
}

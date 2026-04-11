/**
 * Reads the XSRF-TOKEN cookie set by Spring Security's CookieCsrfTokenRepository.
 * Returns null if not present.
 */
export function getCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]+)/)
  return match ? decodeURIComponent(match[1]) : null
}

/**
 * Ensures the CSRF cookie is set by making a GET to /api/auth/csrf.
 * Call once on app startup.
 */
export async function ensureCsrfCookie(): Promise<void> {
  const token = getCsrfToken()
  if (!token) {
    await fetch("/api/auth/csrf", { credentials: "include" })
  }
}

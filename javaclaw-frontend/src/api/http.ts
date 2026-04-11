import { getCsrfToken } from "@/lib/csrf"

export class HttpError extends Error {
  status: number
  body: unknown

  constructor(status: number, message: string, body?: unknown) {
    super(message)
    this.status = status
    this.body = body
    this.name = "HttpError"
  }
}

type OnUnauthorized = () => void

let onUnauthorized: OnUnauthorized = () => {
  if (typeof window !== "undefined" && window.location.pathname !== "/login") {
    window.location.href = "/login"
  }
}

export function setOnUnauthorized(handler: OnUnauthorized) {
  onUnauthorized = handler
}

// REMOVED: buildAuthHeaders — no longer needed, credentials sent via cookie.
// Kept as no-op for any external callers during transition.
export function buildAuthHeaders(extra?: HeadersInit): Headers {
  return new Headers(extra)
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown
  skipAuthRedirect?: boolean
}

const CSRF_METHODS = new Set(["POST", "PUT", "PATCH", "DELETE"])

export async function apiFetch(
  path: string,
  options: RequestOptions = {},
): Promise<Response> {
  const { body, headers: extraHeaders, skipAuthRedirect, method = "GET", ...rest } = options
  const headers = new Headers(extraHeaders)

  // Add CSRF token for mutating methods
  if (CSRF_METHODS.has(method.toUpperCase())) {
    const csrfToken = getCsrfToken()
    if (csrfToken) {
      headers.set("X-XSRF-TOKEN", csrfToken)
    }
  }

  let finalBody: BodyInit | undefined
  if (body !== undefined && body !== null) {
    if (
      body instanceof FormData ||
      body instanceof Blob ||
      body instanceof ArrayBuffer ||
      typeof body === "string"
    ) {
      finalBody = body as BodyInit
    } else {
      if (!headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json")
      }
      finalBody = JSON.stringify(body)
    }
  }

  const response = await fetch(path, {
    ...rest,
    method,
    headers,
    body: finalBody,
    credentials: "include", // Always include cookies
  })

  if (response.status === 401 && !skipAuthRedirect) {
    onUnauthorized()
  }

  return response
}

export async function apiJson<T>(
  path: string,
  options: RequestOptions = {},
): Promise<T> {
  const response = await apiFetch(path, options)
  if (!response.ok) {
    let payload: unknown = undefined
    try {
      payload = await response.json()
    } catch {
      // ignore
    }
    throw new HttpError(
      response.status,
      `HTTP ${response.status} ${response.statusText}`,
      payload,
    )
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

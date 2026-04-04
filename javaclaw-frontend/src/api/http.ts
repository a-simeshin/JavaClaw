import { getStoredCredentials } from "@/store/auth"

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

export function buildAuthHeaders(extra?: HeadersInit): Headers {
  const headers = new Headers(extra)
  const credentials = getStoredCredentials()
  if (credentials && !headers.has("Authorization")) {
    headers.set("Authorization", `Basic ${credentials}`)
  }
  return headers
}

export interface RequestOptions extends Omit<RequestInit, "body"> {
  body?: unknown
  skipAuthRedirect?: boolean
}

export async function apiFetch(
  path: string,
  options: RequestOptions = {},
): Promise<Response> {
  const { body, headers, skipAuthRedirect, ...rest } = options
  const finalHeaders = buildAuthHeaders(headers)
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
      if (!finalHeaders.has("Content-Type")) {
        finalHeaders.set("Content-Type", "application/json")
      }
      finalBody = JSON.stringify(body)
    }
  }

  const response = await fetch(path, {
    ...rest,
    headers: finalHeaders,
    body: finalBody,
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

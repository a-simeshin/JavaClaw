import "@/index.css"

import { QueryClient, QueryClientProvider } from "@tanstack/react-query"
import { createRouter, RouterProvider } from "@tanstack/react-router"
import { Provider as JotaiProvider } from "jotai"
import { StrictMode } from "react"
import { createRoot } from "react-dom/client"
import { Toaster } from "sonner"

import { initTheme } from "@/hooks/use-theme"
import "@/i18n"
import { routeTree } from "./routeTree.gen"

// Apply theme before first paint.
initTheme()

const router = createRouter({ routeTree })

declare module "@tanstack/react-router" {
  interface Register {
    router: typeof router
  }
}

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      refetchOnWindowFocus: false,
    },
  },
})

const rootEl = document.getElementById("root")
if (!rootEl) throw new Error("Root element #root not found")

createRoot(rootEl).render(
  <StrictMode>
    <JotaiProvider>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
        <Toaster position="bottom-right" theme="system" richColors closeButton />
      </QueryClientProvider>
    </JotaiProvider>
  </StrictMode>,
)

import { createFileRoute, useNavigate } from "@tanstack/react-router"
import { useTranslation } from "react-i18next"

import { LoginForm } from "@/components/auth/login-form"

export const Route = createFileRoute("/login")({
  component: LoginPage,
})

function LoginPage() {
  const { t } = useTranslation()
  const navigate = useNavigate()

  return (
    <div className="flex h-full min-h-0 w-full items-center justify-center bg-background px-4 py-10">
      <div
        className="w-full max-w-[360px] animate-[jc-fade-in_0.24s_ease-out] rounded-xl border border-border bg-card p-6 text-card-foreground"
      >
        <div className="mb-5 flex flex-col gap-1">
          <h1 className="font-mono text-[16px] font-bold leading-[1.2] tracking-[-0.02em] text-foreground">
            {t("login.title")}
          </h1>
          <p className="text-[13px] text-muted-foreground">
            {t("login.subtitle")}
          </p>
        </div>
        <LoginForm
          onSuccess={() => {
            void navigate({ to: "/chat" })
          }}
        />
      </div>
    </div>
  )
}

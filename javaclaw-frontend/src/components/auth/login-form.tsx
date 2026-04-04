import { useState } from "react"
import { useTranslation } from "react-i18next"

import { HttpError } from "@/api/http"
import { Input } from "@/components/ui/input"
import { Label } from "@/components/ui/label"
import { useAuth } from "@/hooks/use-auth"
import { cn } from "@/lib/utils"

interface LoginFormProps {
  onSuccess?: () => void
  className?: string
}

export function LoginForm({ onSuccess, className }: LoginFormProps) {
  const { t } = useTranslation()
  const { login } = useAuth()
  const [username, setUsername] = useState("")
  const [password, setPassword] = useState("")
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await login(username, password)
      onSuccess?.()
    } catch (err) {
      if (err instanceof HttpError && (err.status === 401 || err.status === 403)) {
        setError(t("login.error"))
      } else {
        setError(t("login.networkError"))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      className={cn("flex flex-col gap-4", className)}
      noValidate
    >
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="login-username">{t("login.username")}</Label>
        <Input
          id="login-username"
          name="username"
          type="text"
          autoComplete="username"
          autoFocus
          required
          value={username}
          onChange={(event) => setUsername(event.target.value)}
        />
      </div>
      <div className="flex flex-col gap-1.5">
        <Label htmlFor="login-password">{t("login.password")}</Label>
        <Input
          id="login-password"
          name="password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(event) => setPassword(event.target.value)}
        />
      </div>
      {error && (
        <p
          role="alert"
          className="text-[12px] text-error"
        >
          {error}
        </p>
      )}
      <button
        type="submit"
        disabled={submitting || !username || !password}
        className={cn(
          "mt-2 inline-flex h-9 items-center justify-center rounded-sm bg-primary px-3 text-[13px] font-medium text-primary-foreground transition-colors hover:bg-surface-hover",
          "disabled:cursor-not-allowed disabled:opacity-50",
        )}
      >
        {submitting ? t("login.signingIn") : t("login.submit")}
      </button>
    </form>
  )
}

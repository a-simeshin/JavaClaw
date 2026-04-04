import {
  IconLanguage,
  IconLogout,
  IconMoon,
  IconSun,
  IconUser,
} from "@tabler/icons-react"
import { useNavigate } from "@tanstack/react-router"
import { useAtomValue } from "jotai"
import { useTranslation } from "react-i18next"

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu"
import { useAuth } from "@/hooks/use-auth"
import { useTheme } from "@/hooks/use-theme"
import { setLanguage, type SupportedLanguage } from "@/i18n"
import { cn } from "@/lib/utils"
import { authUserAtom } from "@/store/auth"

export function AppHeader() {
  const { t, i18n } = useTranslation()
  const { resolved, setTheme } = useTheme()
  const { logout } = useAuth()
  const user = useAtomValue(authUserAtom)
  const navigate = useNavigate()
  const isDark = resolved === "dark"
  const currentLang = (i18n.resolvedLanguage ?? "en") as SupportedLanguage

  const handleLogout = () => {
    logout()
    void navigate({ to: "/login" })
  }

  return (
    <header className="flex h-full items-center justify-between px-6">
      <div className="flex items-center gap-3">
        <span className="font-mono text-[17px] font-bold tracking-[-0.02em] text-foreground">
          {t("app.console")}
        </span>
      </div>
      <div className="flex items-center gap-1">
        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label={t("header.language")}
            className={cn(
              "inline-flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground transition-colors",
              "hover:bg-surface-hover hover:text-foreground",
              )}
          >
            <IconLanguage width={18} height={18} strokeWidth={1.5} aria-hidden />
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="min-w-36">
            <DropdownMenuLabel>{t("header.language")}</DropdownMenuLabel>
            <DropdownMenuSeparator />
            <DropdownMenuItem
              onClick={() => setLanguage("en")}
              className={cn(currentLang === "en" && "bg-accent")}
            >
              {t("language.en")}
            </DropdownMenuItem>
            <DropdownMenuItem
              onClick={() => setLanguage("ru")}
              className={cn(currentLang === "ru" && "bg-accent")}
            >
              {t("language.ru")}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
        <button
          type="button"
          aria-label={
            isDark ? t("header.theme.light") : t("header.theme.dark")
          }
          onClick={() => setTheme(isDark ? "light" : "dark")}
          className={cn(
            "inline-flex h-8 w-8 items-center justify-center rounded-md text-muted-foreground transition-colors",
            "hover:bg-surface-hover hover:text-foreground",
          )}
        >
          {isDark ? (
            <IconSun width={18} height={18} strokeWidth={1.5} aria-hidden />
          ) : (
            <IconMoon width={18} height={18} strokeWidth={1.5} aria-hidden />
          )}
        </button>
        <DropdownMenu>
          <DropdownMenuTrigger
            aria-label={t("header.userMenu")}
            className={cn(
              "inline-flex h-8 items-center gap-1.5 rounded-md px-2 text-muted-foreground transition-colors",
              "hover:bg-surface-hover hover:text-foreground",
              )}
          >
            <IconUser width={18} height={18} strokeWidth={1.5} aria-hidden />
            {user.username && (
              <span className="text-[13px] text-foreground">
                {user.username}
              </span>
            )}
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="min-w-40">
            {user.username && (
              <>
                <DropdownMenuLabel>
                  <div className="flex flex-col">
                    <span className="text-[13px] text-foreground">
                      {user.username}
                    </span>
                    {user.role && (
                      <span className="text-[11px] text-muted-foreground">
                        {user.role}
                      </span>
                    )}
                  </div>
                </DropdownMenuLabel>
                <DropdownMenuSeparator />
              </>
            )}
            <DropdownMenuItem onClick={handleLogout}>
              <IconLogout
                width={14}
                height={14}
                strokeWidth={1.5}
                aria-hidden
              />
              {t("actions.signOut")}
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  )
}

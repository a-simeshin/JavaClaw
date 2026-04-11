import {
  IconCheck,
  IconDeviceDesktop,
  IconLanguage,
  IconMoon,
  IconPalette,
  IconSun,
  IconUser,
} from "@tabler/icons-react"
import { createFileRoute } from "@tanstack/react-router"
import { useAtomValue } from "jotai"
import type { ComponentType, SVGProps } from "react"
import { useTranslation } from "react-i18next"

import { PageHeader } from "@/components/page-header"
import { useTheme, type Theme } from "@/hooks/use-theme"
import { setLanguage, type SupportedLanguage } from "@/i18n"
import { cn } from "@/lib/utils"
import { authUserAtom } from "@/store/auth"

export const Route = createFileRoute("/settings")({
  component: SettingsPage,
})

/* ------------------------------------------------------------------ */

function SettingsPage() {
  const { t } = useTranslation()

  return (
    <div className="flex flex-col gap-6 p-6">
      <PageHeader
        title={t("settings.title", { defaultValue: "Settings" })}
        subtitle={t("settings.subtitle", {
          defaultValue: "Personalization and preferences",
        })}
      />
      <div className="flex max-w-2xl flex-col gap-6">
        <ProfileSection />
        <ThemeSection />
        <LanguageSection />
      </div>
    </div>
  )
}

/* ------------------------------------------------------------------ */
/*  Profile                                                            */
/* ------------------------------------------------------------------ */

function ProfileSection() {
  const { t } = useTranslation()
  const user = useAtomValue(authUserAtom)

  return (
    <SettingsCard
      icon={IconUser}
      title={t("settings.profile.title", { defaultValue: "Profile" })}
    >
      <div className="grid grid-cols-[auto_1fr] gap-x-6 gap-y-2 text-[13px]">
        <span className="text-muted-foreground">
          {t("settings.profile.username", { defaultValue: "Username" })}
        </span>
        <span className="font-mono text-foreground">
          {user.username || "—"}
        </span>
        <span className="text-muted-foreground">
          {t("settings.profile.role", { defaultValue: "Role" })}
        </span>
        <span className="font-mono text-foreground">
          {user.role || "—"}
        </span>
      </div>
    </SettingsCard>
  )
}

/* ------------------------------------------------------------------ */
/*  Theme                                                              */
/* ------------------------------------------------------------------ */

const THEME_OPTIONS: {
  value: Theme
  labelKey: string
  fallback: string
  icon: ComponentType<SVGProps<SVGSVGElement>>
}[] = [
  {
    value: "dark",
    labelKey: "settings.theme.dark",
    fallback: "Dark",
    icon: IconMoon,
  },
  {
    value: "light",
    labelKey: "settings.theme.light",
    fallback: "Light",
    icon: IconSun,
  },
  {
    value: "system",
    labelKey: "settings.theme.system",
    fallback: "System",
    icon: IconDeviceDesktop,
  },
]

function ThemeSection() {
  const { t } = useTranslation()
  const { theme, setTheme } = useTheme()

  return (
    <SettingsCard
      icon={IconPalette}
      title={t("settings.theme.title", { defaultValue: "Theme" })}
    >
      <div className="flex gap-2">
        {THEME_OPTIONS.map((opt) => {
          const active = theme === opt.value
          return (
            <button
              key={opt.value}
              type="button"
              onClick={() => setTheme(opt.value)}
              className={cn(
                "flex items-center gap-2 rounded-md border px-4 py-2.5 text-[13px] transition-colors duration-150",
                active
                  ? "border-accent-strong bg-surface-selection text-foreground"
                  : "border-border bg-card text-muted-foreground hover:border-accent-muted hover:text-foreground",
              )}
            >
              <opt.icon
                width={16}
                height={16}
                strokeWidth={1.5}
                aria-hidden
              />
              {t(opt.labelKey, { defaultValue: opt.fallback })}
              {active && (
                <IconCheck
                  width={14}
                  height={14}
                  strokeWidth={2}
                  className="text-accent-strong"
                  aria-hidden
                />
              )}
            </button>
          )
        })}
      </div>
    </SettingsCard>
  )
}

/* ------------------------------------------------------------------ */
/*  Language                                                           */
/* ------------------------------------------------------------------ */

const LANG_OPTIONS: {
  value: SupportedLanguage
  labelKey: string
  fallback: string
}[] = [
  { value: "en", labelKey: "language.en", fallback: "English" },
  { value: "ru", labelKey: "language.ru", fallback: "Русский" },
]

function LanguageSection() {
  const { t, i18n } = useTranslation()
  const currentLang = (i18n.resolvedLanguage ?? "en") as SupportedLanguage

  return (
    <SettingsCard
      icon={IconLanguage}
      title={t("settings.language.title", { defaultValue: "Language" })}
    >
      <div className="flex gap-2">
        {LANG_OPTIONS.map((opt) => {
          const active = currentLang === opt.value
          return (
            <button
              key={opt.value}
              type="button"
              onClick={() => setLanguage(opt.value)}
              className={cn(
                "flex items-center gap-2 rounded-md border px-4 py-2.5 text-[13px] transition-colors duration-150",
                active
                  ? "border-accent-strong bg-surface-selection text-foreground"
                  : "border-border bg-card text-muted-foreground hover:border-accent-muted hover:text-foreground",
              )}
            >
              {t(opt.labelKey, { defaultValue: opt.fallback })}
              {active && (
                <IconCheck
                  width={14}
                  height={14}
                  strokeWidth={2}
                  className="text-accent-strong"
                  aria-hidden
                />
              )}
            </button>
          )
        })}
      </div>
    </SettingsCard>
  )
}

/* ------------------------------------------------------------------ */
/*  Shared card wrapper                                                */
/* ------------------------------------------------------------------ */

function SettingsCard({
  icon: Icon,
  title,
  children,
}: {
  icon: ComponentType<SVGProps<SVGSVGElement>>
  title: string
  children: React.ReactNode
}) {
  return (
    <div className="rounded-lg border border-border bg-card p-5">
      <div className="mb-4 flex items-center gap-2">
        <Icon
          width={16}
          height={16}
          strokeWidth={1.5}
          className="text-muted-foreground"
          aria-hidden
        />
        <h2 className="font-mono text-[13px] font-semibold uppercase tracking-[0.08em] text-foreground">
          {title}
        </h2>
      </div>
      {children}
    </div>
  )
}

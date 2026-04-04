import i18n from "i18next"
import LanguageDetector from "i18next-browser-languagedetector"
import { initReactI18next } from "react-i18next"

import en from "./locales/en.json"
import ru from "./locales/ru.json"

export const SUPPORTED_LANGUAGES = ["en", "ru"] as const
export type SupportedLanguage = (typeof SUPPORTED_LANGUAGES)[number]

const STORAGE_KEY = "javaclaw.lang"

if (!i18n.isInitialized) {
  void i18n
    .use(LanguageDetector)
    .use(initReactI18next)
    .init({
      resources: {
        en: { translation: en },
        ru: { translation: ru },
      },
      fallbackLng: "en",
      supportedLngs: SUPPORTED_LANGUAGES as unknown as string[],
      interpolation: {
        escapeValue: false,
      },
      detection: {
        order: ["localStorage", "navigator"],
        lookupLocalStorage: STORAGE_KEY,
        caches: ["localStorage"],
      },
      returnNull: false,
    })
}

export function setLanguage(lng: SupportedLanguage) {
  void i18n.changeLanguage(lng)
  if (typeof window !== "undefined") {
    window.localStorage.setItem(STORAGE_KEY, lng)
  }
}

export default i18n
